package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.PurchaseDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.purchase.PurchaseLine
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.request.OrderPartRequest
import org.mr0btain.ticketman.server.request.PurchaseOrderRequest
import org.mr0btain.ticketman.server.request.ReceiveRequest
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Suppliers and ordering parts from them. An order is put together as a draft, placed, then received as the parts
 * arrive, each arrival going into stock through `InventoryDatabase.adjustStock`. Lines ordered for a ticket move it
 * to the shop's [org.mr0btain.ticketman.data.shop.ShopProfile.partsOrderedStatus] when the order is placed, and to
 * its `partsArrivedStatus` once every part ordered for it has arrived
 * @param now Epoch millis, swapped in tests
 */
class PurchaseService(
    private val purchases: PurchaseDatabase,
    private val inventory: InventoryDatabase,
    private val tickets: TicketDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val moves = TicketMoves(tickets, shop, now)

    // ------------------------------------------ Suppliers ------------------------------------------

    fun addSupplier(supplier: Supplier, actor: Uuid?): AccountResult<Supplier> {
        supplierProblem(supplier)?.let { return AccountResult.Invalid(it) }
        if (purchases.supplier(supplier.identifier) != null) return AccountResult.Conflict("That supplier already exists")
        if (!purchases.addSupplier(supplier)) return AccountResult.Invalid("Couldn't save the supplier")

        auditLog.log(actor, AuditAction.CREATE, "supplier", supplier.identifier.toString(), supplier.name.trim())
        return AccountResult.Done(purchases.supplier(supplier.identifier)!!)
    }

    fun updateSupplier(supplier: Supplier, actor: Uuid?): AccountResult<Supplier> {
        supplierProblem(supplier)?.let { return AccountResult.Invalid(it) }
        if (!purchases.updateSupplier(supplier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.UPDATE, "supplier", supplier.identifier.toString(), supplier.name.trim())
        return AccountResult.Done(purchases.supplier(supplier.identifier)!!)
    }

    /** Orders keep the supplier's name; items that pointed at it just have no supplier any more */
    fun removeSupplier(identifier: Uuid, actor: Uuid?): AccountResult<Unit> {
        val supplier = purchases.supplier(identifier) ?: return AccountResult.NotFound
        if (!purchases.removeSupplier(identifier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.DELETE, "supplier", identifier.toString(), supplier.name)
        return AccountResult.Done(Unit)
    }

    private fun supplierProblem(supplier: Supplier): String? = when {
        supplier.name.isBlank() -> "Give the supplier a name"
        supplier.name.trim().length > MAX_NAME -> "A supplier's name can be up to $MAX_NAME characters"
        else -> null
    }

    // ------------------------------------------ Orders ------------------------------------------

    /** A new draft */
    fun create(request: PurchaseOrderRequest, staffName: String, actor: Uuid?): AccountResult<PurchaseOrder> = synchronized(LOCK) {
        val supplierName = supplierName(request.supplierIdentifier) ?: return AccountResult.Invalid("That supplier doesn't exist")
        val lines = linesFor(request.lines) ?: return AccountResult.Invalid(linesProblem(request.lines)!!)

        val order = purchases.add(
            PurchaseOrder(
                supplierIdentifier = request.supplierIdentifier,
                supplierName = supplierName,
                lines = lines,
                note = request.note.trim(),
                createdAt = now(),
                createdBy = staffName,
                expectedAt = request.expectedAt
            )
        ) ?: return AccountResult.Invalid("Couldn't save the order")

        auditLog.log(actor, AuditAction.CREATE, "purchase order", order.identifier.toString(), "${order.label} drafted${supplierName.ifBlank { null }?.let { " for $it" }.orEmpty()}")
        return AccountResult.Done(order)
    }

    /** Changes a draft's supplier, lines and note */
    fun update(identifier: Uuid, request: PurchaseOrderRequest, actor: Uuid?): AccountResult<PurchaseOrder> = synchronized(LOCK) {
        val order = purchases.order(identifier) ?: return AccountResult.NotFound
        if (order.status != PurchaseStatus.DRAFT) return AccountResult.Conflict("${order.label} was placed, only a draft can change")
        val supplierName = supplierName(request.supplierIdentifier) ?: return AccountResult.Invalid("That supplier doesn't exist")
        val lines = linesFor(request.lines) ?: return AccountResult.Invalid(linesProblem(request.lines)!!)

        val changed = order.copy(
            supplierIdentifier = request.supplierIdentifier,
            supplierName = supplierName,
            lines = lines,
            note = request.note.trim(),
            expectedAt = request.expectedAt
        )
        if (!purchases.save(changed)) return AccountResult.Invalid("Couldn't save the order")

        auditLog.log(actor, AuditAction.UPDATE, "purchase order", identifier.toString(), "${order.label} changed")
        return AccountResult.Done(changed)
    }

    /** Sends a draft to the supplier. Tickets with parts on it move to the shop's parts ordered status */
    fun place(identifier: Uuid, staffName: String, actor: Uuid?): AccountResult<PurchaseOrder> = synchronized(LOCK) {
        val order = purchases.order(identifier) ?: return AccountResult.NotFound
        if (order.status != PurchaseStatus.DRAFT) return AccountResult.Conflict("${order.label} is ${order.status.label.lowercase()} already")
        if (order.lines.isEmpty()) return AccountResult.Invalid("Add at least one part before placing the order")
        if (order.supplierIdentifier == null) return AccountResult.Invalid("Pick who it's ordered from")

        val placed = order.copy(status = PurchaseStatus.ORDERED, orderedAt = now())
        if (!purchases.save(placed)) return AccountResult.Invalid("Couldn't save the order")

        val status = shop.get().partsOrderedStatus
        order.lines.mapNotNull { it.ticketIdentifier }.distinct().forEach { ticket -> moves.move(ticket, status, staffName) }

        auditLog.log(actor, AuditAction.UPDATE, "purchase order", identifier.toString(), "${order.label} placed with ${order.supplierName}")
        return AccountResult.Done(placed)
    }

    /**
     * Takes in parts that arrived: each line's count goes up, they go into stock, and the item's cost becomes what
     * this order paid. All together or not at all. Tickets whose parts have all arrived move to the shop's parts
     * arrived status
     */
    fun receive(identifier: Uuid, request: ReceiveRequest, staffName: String, actor: Uuid?): AccountResult<PurchaseOrder> = synchronized(LOCK) {
        val order = purchases.order(identifier) ?: return AccountResult.NotFound
        if (!order.status.isOpen) return AccountResult.Conflict("${order.label} is ${order.status.label.lowercase()}, nothing can be received on it")

        val arriving = request.lines.filter { it.quantity != 0 }.groupBy { it.lineIdentifier }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        if (arriving.isEmpty()) return AccountResult.Invalid("Say how many of something arrived")
        arriving.forEach { (lineIdentifier, quantity) ->
            val line = order.lines.firstOrNull { it.identifier == lineIdentifier } ?: return AccountResult.Invalid("That line isn't on ${order.label}")
            if (quantity < 0) return AccountResult.Invalid("Receive a number of parts, more than zero")
            if (quantity > line.outstanding) return AccountResult.Invalid("Only ${line.outstanding} of ${line.description} are still to come on ${order.label}")
        }

        val lines = order.lines.map { line -> arriving[line.identifier]?.let { line.copy(receivedQuantity = line.receivedQuantity + it) } ?: line }
        val complete = lines.all { it.outstanding == 0 }
        val received = order.copy(
            lines = lines,
            status = if (complete) PurchaseStatus.RECEIVED else PurchaseStatus.PARTLY_RECEIVED,
            receivedAt = if (complete) now() else order.receivedAt
        )

        transaction {
            if (!purchases.save(received)) throw IllegalStateException("Order not saved")
            order.lines.filter { it.identifier in arriving }.forEach { line ->
                // Untracked items stay untracked, adjustStock leaves them alone
                inventory.adjustStock(line.inventoryIdentifier, -arriving.getValue(line.identifier))
                if (line.unitCostCents > 0) inventory.setCost(line.inventoryIdentifier, line.unitCostCents)
            }

            val count = arriving.values.sum()
            auditLog.log(actor, AuditAction.UPDATE, "purchase order", identifier.toString(), "Received $count part${if (count == 1) "" else "s"} on ${order.label}")
        }

        // Every ticket on this order whose parts, on every order, have now all come
        val status = shop.get().partsArrivedStatus
        if (status != null) {
            val open = purchases.unfinished().orEmpty().filter { it.status.isOpen }
            lines.filter { it.identifier in arriving }.mapNotNull { it.ticketIdentifier }.distinct()
                .filter { ticket -> open.none { other -> other.lines.any { it.ticketIdentifier == ticket && it.outstanding > 0 } } }
                .forEach { ticket -> moves.move(ticket, status, staffName) }
        }

        return AccountResult.Done(received)
    }

    /** Stops waiting on what hasn't come. What arrived stays in stock */
    fun cancel(identifier: Uuid, actor: Uuid?): AccountResult<PurchaseOrder> = synchronized(LOCK) {
        val order = purchases.order(identifier) ?: return AccountResult.NotFound
        if (order.status == PurchaseStatus.RECEIVED || order.status == PurchaseStatus.CANCELLED) {
            return AccountResult.Conflict("${order.label} is ${order.status.label.lowercase()}")
        }

        val cancelled = order.copy(status = PurchaseStatus.CANCELLED)
        if (!purchases.save(cancelled)) return AccountResult.Invalid("Couldn't save the order")

        auditLog.log(actor, AuditAction.UPDATE, "purchase order", identifier.toString(), "${order.label} cancelled")
        return AccountResult.Done(cancelled)
    }

    /** Deletes a draft that was never placed */
    fun remove(identifier: Uuid, actor: Uuid?): AccountResult<Unit> = synchronized(LOCK) {
        val order = purchases.order(identifier) ?: return AccountResult.NotFound
        if (order.status != PurchaseStatus.DRAFT) return AccountResult.Conflict("${order.label} was placed, cancel it instead")
        if (!purchases.remove(identifier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.DELETE, "purchase order", identifier.toString(), "${order.label} deleted")
        return AccountResult.Done(Unit)
    }

    /**
     * A part a ticket needs: added to the supplier's newest draft, or a new draft when there's none. The ticket
     * moves on when that order is placed
     */
    fun orderPart(request: OrderPartRequest, staffName: String, actor: Uuid?): AccountResult<PurchaseOrder> = synchronized(LOCK) {
        val ticket = tickets.getById(request.ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was picked up")
        val item = inventory.getById(request.inventoryIdentifier) ?: return AccountResult.Invalid("That inventory item doesn't exist")
        if (request.quantity <= 0) return AccountResult.Invalid("Order at least one")

        val supplier = request.supplierIdentifier ?: item.supplierIdentifier ?: return AccountResult.Invalid("Pick who to order ${item.name} from")
        val supplierName = supplierName(supplier) ?: return AccountResult.Invalid("That supplier doesn't exist")
        val line = PurchaseLine(
            inventoryIdentifier = item.identifier,
            description = item.name,
            quantity = request.quantity,
            unitCostCents = item.costCents,
            ticketIdentifier = ticket.identifier
        )

        val draft = purchases.unfinished().orEmpty().lastOrNull { it.status == PurchaseStatus.DRAFT && it.supplierIdentifier == supplier }
        val order = if (draft != null) {
            draft.copy(lines = draft.lines + line).takeIf { purchases.save(it) } ?: return AccountResult.Invalid("Couldn't save the order")
        } else {
            purchases.add(PurchaseOrder(supplierIdentifier = supplier, supplierName = supplierName, lines = listOf(line), createdAt = now(), createdBy = staffName))
                ?: return AccountResult.Invalid("Couldn't save the order")
        }

        auditLog.log(actor, AuditAction.UPDATE, "ticket", ticket.identifier.toString(), "${request.quantity} x ${item.name} for ${ticket.label} on ${order.label}")
        return AccountResult.Done(order)
    }

    // The supplier's name to keep on the order, blank for none; null when it doesn't exist
    private fun supplierName(identifier: Uuid?): String? =
        if (identifier == null) "" else purchases.supplier(identifier)?.name

    // The lines with their items' names filled in and nothing received yet, or null when one is wrong
    private fun linesFor(lines: List<PurchaseLine>): List<PurchaseLine>? {
        if (linesProblem(lines) != null) return null
        return lines.map { line ->
            line.copy(description = line.description.trim().ifBlank { inventory.getById(line.inventoryIdentifier)!!.name }, receivedQuantity = 0)
        }
    }

    private fun linesProblem(lines: List<PurchaseLine>): String? {
        lines.forEach { line ->
            if (line.quantity <= 0) return "Order at least one of each part"
            if (line.unitCostCents < 0) return "Costs can't be negative"
            if (inventory.getById(line.inventoryIdentifier) == null) return "Every line needs an inventory item that exists"
            line.ticketIdentifier?.let { if (tickets.getById(it) == null) return "A line is for a ticket that doesn't exist" }
        }
        return null
    }

    companion object {
        const val MAX_NAME = 120

        // Changes that check an order and then save it run one at a time, so two receipts can't both count
        private val LOCK = Any()
    }
}

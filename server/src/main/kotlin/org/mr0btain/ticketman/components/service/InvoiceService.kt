package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.components.database.WorkDatabase
import org.mr0btain.ticketman.components.utils.formatQuantity
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Building invoices, taking payments, logging work on tickets and picking up tickets
 */
class InvoiceService(
    private val invoices: InvoiceDatabase,
    private val tickets: TicketDatabase,
    private val inventory: InventoryDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase,
    private val work: WorkDatabase = WorkDatabase(),
    private val customers: CustomerDatabase = CustomerDatabase()
) {
    /** The ticket's invoice, made if it doesn't have one */
    fun forTicket(ticketIdentifier: Uuid, actor: Uuid?): AccountResult<Invoice> {
        tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound

        val existing = invoices.getByTicket(ticketIdentifier)
        if (existing != null) return AccountResult.Done(existing)

        val customer = tickets.getCustomer(ticketIdentifier)?.identifier
        val invoice = invoices.getOrCreate(ticketIdentifier, customer, taxRate()) ?: return AccountResult.Invalid("Couldn't make an invoice")

        auditLog.log(actor, AuditAction.CREATE, "invoice", invoice.identifier.toString(), "Invoice ${invoice.label}")
        return AccountResult.Done(invoice)
    }

    /**
     * Applies what someone changed on an open invoice to the lines it has now, so a line added meanwhile (like logged
     * work) stays. A line someone else changed or removed since is a conflict. Keeps the tax rate the invoice was made
     * with, so changing the shop's rate doesn't change what a customer was already quoted
     */
    fun update(identifier: Uuid, request: InvoiceChangeRequest, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()} and can't be changed")
        if (request.isEmpty) return AccountResult.Done(invoice)

        val (charges, discount) = when (val outcome = InvoiceChanges.apply(invoice.charges, invoice.discountCents, request)) {
            is InvoiceChanges.Outcome.Applied -> outcome.charges to outcome.discountCents
            is InvoiceChanges.Outcome.Conflict -> return AccountResult.Conflict(outcome.message)
        }

        chargesProblem(charges)?.let { return AccountResult.Invalid(it) }
        if (discount < 0) return AccountResult.Invalid("Discount can't be negative")

        val saved = invoices.setCharges(identifier, charges, discount, invoice.taxRateBasisPoints)
            ?: return AccountResult.Invalid("Couldn't save the invoice")

        auditLog.log(actor, AuditAction.UPDATE, "invoice", identifier.toString(), "Invoice ${invoice.label} total ${money(saved.totalCents)}")
        return AccountResult.Done(saved)
    }

    /** Takes a payment, like a deposit at check in */
    fun addPayment(identifier: Uuid, request: PaymentRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}")
        if (request.amountCents <= 0) return AccountResult.Invalid("Payment has to be more than zero")

        val saved = invoices.addPayment(identifier, request.toPayment(receivedBy)) ?: return AccountResult.Invalid("Couldn't save the payment")

        auditLog.log(actor, AuditAction.CREATE, "payment", identifier.toString(), "${money(request.amountCents)} ${request.method.name.lowercase()} on ${invoice.label}")
        return AccountResult.Done(saved)
    }

    /** Takes a payment back off an open invoice, for one entered by mistake */
    fun removePayment(identifier: Uuid, paymentIdentifier: Uuid, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Payments on a ${invoice.status.name.lowercase()} invoice can't be removed")
        if (!invoices.removePayment(identifier, paymentIdentifier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.DELETE, "payment", identifier.toString(), "Removed a payment from ${invoice.label}")
        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    /**
     * Hands the ticket back. Everything happens together or not at all: the payments are taken, the invoice has to
     * end up paid in full, parts come out of stock and the ticket is closed. A part without enough in stock stops
     * the pickup, so the count gets fixed instead of going below zero
     */
    fun pickup(request: PickupRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val ticket = tickets.getById(request.ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was already picked up")
        if (request.payments.any { it.amountCents <= 0 }) return AccountResult.Invalid("Payments have to be more than zero")

        val invoice = when (val result = forTicket(request.ticketIdentifier, actor)) {
            is AccountResult.Done -> result.value
            else -> return AccountResult.Invalid("Couldn't find or make the invoice")
        }

        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}")

        val paying = request.payments.sumOf { it.amountCents }
        val owed = invoice.balanceCents - paying
        if (owed > 0) return AccountResult.Conflict("${money(owed)} is still owed on ${invoice.label}")

        val parts = partsUsed(invoice.charges)
        parts.forEach { (identifier, quantity) ->
            val item = inventory.getById(identifier) ?: return@forEach
            if (item.stock != Int.MAX_VALUE && item.stock < quantity) return shortOf(identifier)
        }

        val now = Clock.System.now().toEpochMilliseconds()

        try {
            transaction {
                request.payments.forEach { payment -> invoices.addPayment(invoice.identifier, payment.toPayment(receivedBy)) }

                // A count set by hand since the check above can still leave too few, which undoes the lot
                parts.forEach { (identifier, quantity) ->
                    if (!inventory.adjustStock(identifier, quantity)) throw ShortOfStock(identifier)
                }

                invoices.setStatus(invoice.identifier, InvoiceStatus.PAID, paidAt = now)
                tickets.markPickedUp(ticket.identifier, now)
                tickets.recordStatusChange(ticket.identifier, TicketStatus(TicketStatusType.PICKED_UP), receivedBy)

                // Part of the transaction, so it's only there if the pickup is
                auditLog.log(actor, AuditAction.UPDATE, "ticket", ticket.identifier.toString(), "Ticket ${ticket.label} picked up, ${invoice.label} paid ${money(invoice.totalCents)}")
            }
        } catch (ex: ShortOfStock) {
            return shortOf(ex.identifier)
        }

        return AccountResult.Done(invoices.getById(invoice.identifier)!!)
    }

    /**
     * Sells straight from stock with no ticket. Like a pickup it's all or nothing: the invoice is made with its
     * lines, the payments have to cover it, the parts come out of stock and it's saved PAID
     */
    fun sell(request: SaleRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        linesProblem(request.charges)?.let { return AccountResult.Invalid(it) }
        if (request.discountCents < 0) return AccountResult.Invalid("Discount can't be negative")
        if (request.payments.any { it.amountCents <= 0 }) return AccountResult.Invalid("Payments have to be more than zero")
        request.customerIdentifier?.let { if (customers.getById(it) == null) return AccountResult.Invalid("That customer doesn't exist") }

        val taxRate = taxRate()
        val totals = InvoiceMath.totals(request.charges, request.discountCents, taxRate)
        val owed = totals.totalCents - request.payments.sumOf { it.amountCents }
        if (owed > 0) return AccountResult.Conflict("${money(owed)} is still owed")

        val parts = partsUsed(request.charges)
        parts.forEach { (identifier, quantity) ->
            val item = inventory.getById(identifier) ?: return@forEach
            if (item.stock != Int.MAX_VALUE && item.stock < quantity) return shortOf(identifier)
        }

        val now = Clock.System.now().toEpochMilliseconds()

        val sold = try {
            invoices.whileNumbering {
                transaction {
                    val invoice = invoices.createSale(request.customerIdentifier, taxRate) ?: throw IllegalStateException("Sale not made")
                    invoices.setCharges(invoice.identifier, request.charges, request.discountCents, taxRate)
                    request.payments.forEach { payment -> invoices.addPayment(invoice.identifier, payment.toPayment(receivedBy)) }

                    parts.forEach { (identifier, quantity) ->
                        if (!inventory.adjustStock(identifier, quantity)) throw ShortOfStock(identifier)
                    }

                    invoices.setStatus(invoice.identifier, InvoiceStatus.PAID, paidAt = now)
                    auditLog.log(actor, AuditAction.CREATE, "invoice", invoice.identifier.toString(), "Sold ${invoice.label}, ${money(totals.totalCents)}")
                    invoice.identifier
                }
            }
        } catch (ex: ShortOfStock) {
            return shortOf(ex.identifier)
        }

        return AccountResult.Done(invoices.getById(sold)!!)
    }

    /**
     * Cancels an invoice. A paid one puts its parts back in stock. The ticket stays as it is
     */
    fun void(identifier: Uuid, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status == InvoiceStatus.VOID) return AccountResult.Conflict("Invoice ${invoice.label} is already void")

        transaction {
            if (invoice.status == InvoiceStatus.PAID) {
                partsUsed(invoice.charges).forEach { (identifier, quantity) -> inventory.adjustStock(identifier, -quantity) }
            }

            invoices.setStatus(identifier, InvoiceStatus.VOID)
            auditLog.log(actor, AuditAction.DELETE, "invoice", identifier.toString(), "Voided ${invoice.label}")
        }

        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    /**
     * Deletes an invoice kept after its ticket was deleted, once an admin decides it isn't needed. Stock taken for
     * its parts stays taken; void it first to put that back. One whose ticket is still there goes with the ticket
     */
    fun remove(identifier: Uuid, actor: Uuid?): AccountResult<Unit> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (!invoice.ticketRemoved) return AccountResult.Conflict("Invoice ${invoice.label} still has its ticket, it's deleted with the ticket")

        transaction {
            if (!invoices.remove(identifier)) throw IllegalStateException("Invoice ${invoice.label} wasn't deleted")
            auditLog.log(actor, AuditAction.DELETE, "invoice", identifier.toString(), "Deleted ${invoice.label} (ticket ${invoice.ticketLabel}, ${money(invoice.paidCents)} paid)")
        }

        return AccountResult.Done(Unit)
    }

    /** The ticket's invoice, without making one */
    fun invoiceFor(ticketIdentifier: Uuid): Invoice? = invoices.getByTicket(ticketIdentifier)

    // ------------------------------------------ Estimates ------------------------------------------

    /** What's wrong with lines for an invoice or an estimate, or null */
    fun linesProblem(charges: List<Charge>): String? =
        if (charges.isEmpty()) "Add at least one line" else chargesProblem(charges)

    /**
     * Puts an approved estimate's lines on the ticket's open invoice, made then if it has none. [alongside] runs in
     * the same transaction, first: when it returns false (the estimate was answered meanwhile) nothing is saved
     */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun addApprovedLines(ticketIdentifier: Uuid, lines: List<Charge>, label: String, actor: Uuid?, alongside: () -> Boolean): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was already picked up")

        // Fresh ids, so lines copied from the invoice into an estimate don't clash with themselves
        val charges = lines.map { it.copy(identifier = Uuid.generateV7()) }
        chargesProblem(charges)?.let { return AccountResult.Invalid(it) }

        val invoice = when (val result = forTicket(ticketIdentifier, actor)) {
            is AccountResult.Done -> result.value
            else -> return AccountResult.Invalid("Couldn't find or make the invoice")
        }
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}, the estimate can't be added to it")

        val saved = try {
            transaction {
                if (!alongside()) throw AlreadyAnswered()
                invoices.setCharges(invoice.identifier, invoice.charges + charges, invoice.discountCents, invoice.taxRateBasisPoints)
                    ?: throw IllegalStateException("Invoice not saved")
            }
        } catch (_: AlreadyAnswered) {
            return AccountResult.Conflict("That estimate was already answered")
        }

        auditLog.log(actor, AuditAction.UPDATE, "invoice", invoice.identifier.toString(), "$label added to ${invoice.label}, total ${money(saved.totalCents)}")
        return AccountResult.Done(saved)
    }

    private class AlreadyAnswered : Exception()

    // ------------------------------------------ Work log ------------------------------------------

    /**
     * Logs time or a part on a ticket and puts the matching line on its invoice, made then if it has none. Both are
     * saved together. Stock is still taken at pickup, from the invoice's lines
     * @param userIdentifier Who's logging it, kept on the entry with [userName]
     */
    fun logWork(ticketIdentifier: Uuid, request: WorkRequest, userIdentifier: Uuid?, userName: String): AccountResult<WorkEntry> =
        when (val result = logWork(ticketIdentifier, listOf(request), userIdentifier, userName)) {
            is AccountResult.Done -> AccountResult.Done(result.value.single())
            is AccountResult.NotFound -> AccountResult.NotFound
            is AccountResult.Invalid -> result
            is AccountResult.Conflict -> result
        }

    /**
     * Logs several things at once, like the time and the parts for one repair: all of them with their invoice lines,
     * or none if one is wrong
     * @return The entries as saved, in the order sent
     */
    fun logWork(ticketIdentifier: Uuid, requests: List<WorkRequest>, userIdentifier: Uuid?, userName: String): AccountResult<List<WorkEntry>> = synchronized(CHANGE_LOCK) {
        if (requests.isEmpty()) return AccountResult.Invalid("Nothing to log")
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was picked up, its work can't change")

        val workedAt = Clock.System.now().toEpochMilliseconds()
        val entries = requests.map { request ->
            val item = request.inventoryIdentifier?.let { inventory.getById(it) ?: return AccountResult.Invalid("That inventory item doesn't exist") }
            if (request.type == WorkType.PART && item == null) return AccountResult.Invalid("Pick the part from inventory")

            WorkEntry(
                type = request.type,
                description = request.description.trim().ifBlank { item?.name.orEmpty() },
                quantity = request.quantity,
                unitPriceCents = request.unitPriceCents ?: if (request.type == WorkType.PART) item!!.priceCents else shop.get().laborRateCents,
                inventoryIdentifier = item?.identifier,
                userIdentifier = userIdentifier,
                userName = userName,
                workedAt = workedAt
            )
        }
        val charges = entries.map { it.toCharge() }
        chargesProblem(charges)?.let { return AccountResult.Invalid(it) }

        val invoice = when (val result = forTicket(ticketIdentifier, userIdentifier)) {
            is AccountResult.Done -> result.value
            else -> return AccountResult.Invalid("Couldn't find or make the invoice")
        }
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}, work can't be added to it")

        transaction {
            entries.forEach { work.add(ticketIdentifier, it) }
            invoices.setCharges(invoice.identifier, invoice.charges + charges, invoice.discountCents, invoice.taxRateBasisPoints)
            auditLog.log(userIdentifier, AuditAction.CREATE, "ticket", ticketIdentifier.toString(), "Logged ${entries.joinToString(", ") { describe(it) }} on ${ticket.label}")
        }

        return AccountResult.Done(entries)
    }

    /**
     * Takes a work entry off its ticket, and its line off the invoice. Not once the invoice is paid or void, since
     * the work log should match what was charged
     */
    fun removeWork(identifier: Uuid, actor: Uuid?): AccountResult<Unit> = synchronized(CHANGE_LOCK) {
        val (ticketIdentifier, entry) = work.get(identifier) ?: return AccountResult.NotFound
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was picked up, its work can't change")

        val invoice = invoices.getByTicket(ticketIdentifier)
        if (invoice != null && invoice.status != InvoiceStatus.OPEN) {
            return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}, its work can't change")
        }

        transaction {
            work.remove(identifier)
            // Taken off by hand at the counter already is fine, there's nothing to take off then
            if (invoice != null && invoice.charges.any { it.identifier == identifier }) {
                invoices.setCharges(invoice.identifier, invoice.charges.filter { it.identifier != identifier }, invoice.discountCents, invoice.taxRateBasisPoints)
            }
            auditLog.log(actor, AuditAction.DELETE, "ticket", ticketIdentifier.toString(), "Removed ${describe(entry)} from ${ticket.label}")
        }

        return AccountResult.Done(Unit)
    }

    // Labor isn't taxed and parts are, which can be changed on the invoice afterwards
    private fun WorkEntry.toCharge(): Charge =
        Charge(
            identifier = identifier,
            type = if (type == WorkType.PART) ChargeType.PART else ChargeType.LABOR,
            description = description,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            inventoryIdentifier = inventoryIdentifier,
            taxable = type == WorkType.PART
        )

    private fun describe(entry: WorkEntry): String =
        if (entry.type == WorkType.PART) "${formatQuantity(entry.quantity)} x ${entry.description}"
        else "${formatQuantity(entry.quantity)}h ${entry.description}"

    private class ShortOfStock(val identifier: Uuid) : Exception()

    private fun shortOf(identifier: Uuid): AccountResult.Conflict {
        val item = inventory.getById(identifier)
        return AccountResult.Conflict("Only ${item?.stock ?: 0} of ${item?.name?.ifBlank { null } ?: "an item"} in stock, fix the count in Inventory first")
    }

    // How many of each stocked item the part lines use, added up when one is on several lines
    private fun partsUsed(charges: List<Charge>): Map<Uuid, Int> =
        charges
            .filter { it.type == ChargeType.PART && it.inventoryIdentifier != null }
            .groupBy { it.inventoryIdentifier!! }
            .mapValues { (_, charges) -> charges.sumOf { it.quantity.roundToLong().toInt() } }

    // Tax rate from the shop profile, nothing if it's blank or not a number
    private fun taxRate(): Int = Money.percentToBasisPoints(shop.get().taxRate) ?: 0

    // An amount in the shop's currency, for messages and the audit log
    private fun money(cents: Long): String = Money.format(cents, shop.get().currencySymbol)

    private fun chargesProblem(charges: List<Charge>): String? {
        charges.forEach { charge ->
            when {
                charge.quantity <= 0 -> return "Quantity has to be more than zero"
                abs(charge.quantity * 100 - (charge.quantity * 100).roundToLong()) > 1e-6 -> return "Quantity can only have two decimals"
                charge.unitPriceCents < 0 -> return "Prices can't be negative"
                charge.description.isBlank() -> return "Every line needs a description"
                charge.type == ChargeType.PART && charge.inventoryIdentifier == null -> return "Parts need an inventory item"
                charge.type == ChargeType.PART && charge.quantity % 1.0 != 0.0 -> return "Parts are counted in whole numbers"
                charge.inventoryIdentifier != null && inventory.getById(charge.inventoryIdentifier!!) == null -> return "Inventory item for \"${charge.description}\" doesn't exist"
            }
        }

        return null
    }

    private fun PaymentRequest.toPayment(receivedBy: String): Payment =
        Payment(
            method = method,
            amountCents = amountCents,
            receivedAt = Clock.System.now().toEpochMilliseconds(),
            receivedBy = receivedBy,
            note = note.trim()
        )

    companion object {
        /**
         * Changes that check an invoice's state and then change it run one at a time, so two pickups of the same
         * ticket can't both pass the checks before either is saved
         */
        private val CHANGE_LOCK = Any()
    }
}

package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.purchase.PurchaseOrderTable
import org.mr0btain.ticketman.components.database.tables.purchase.SupplierTable
import org.mr0btain.ticketman.data.purchase.PurchaseNumber
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.data.shared.PagedRequest
import java.time.Year
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Suppliers and purchase orders. Rules like only changing drafts are the purchase service's
 * @param currentYear Year new order numbers are made in
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class PurchaseDatabase(
    private val currentYear: () -> Int = { Year.now().value }
) {
    private val TAG = "PurchaseDatabase"
    private val SUPPLIER_TAG = "$TAG:Supplier"
    private val ORDER_TAG = "$TAG:Order"
    private val LIST_TAG = "$TAG:List"

    // ------------------------------------------ Suppliers ------------------------------------------

    /** By name. Null when the read failed */
    fun suppliers(): List<Supplier>? =
        dbQuery(SUPPLIER_TAG) {
            SupplierTable.selectAll().orderBy(SupplierTable.name.lowerCase() to SortOrder.ASC).map { it.toSupplier() }
        }

    fun supplier(identifier: Uuid): Supplier? =
        dbQuery(SUPPLIER_TAG) { SupplierTable.selectAll().where { SupplierTable.id eq identifier }.singleOrNull()?.toSupplier() }

    fun addSupplier(supplier: Supplier): Boolean =
        dbWrite(SUPPLIER_TAG) {
            SupplierTable.insert { row ->
                row[SupplierTable.id] = supplier.identifier
                row.fill(supplier)
            }
            true
        }

    fun updateSupplier(supplier: Supplier): Boolean =
        dbWrite(SUPPLIER_TAG) { SupplierTable.update(where = { SupplierTable.id eq supplier.identifier }) { row -> row.fill(supplier) } > 0 }

    fun removeSupplier(identifier: Uuid): Boolean =
        dbWrite(SUPPLIER_TAG) { SupplierTable.deleteWhere { SupplierTable.id eq identifier } > 0 }

    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.fill(supplier: Supplier) {
        this[SupplierTable.name] = supplier.name.trim()
        this[SupplierTable.contactName] = supplier.contactName.trim()
        this[SupplierTable.phone] = supplier.phone.trim()
        this[SupplierTable.email] = supplier.email.trim()
        this[SupplierTable.website] = supplier.website.trim()
        this[SupplierTable.accountNumber] = supplier.accountNumber.trim()
        this[SupplierTable.notes] = supplier.notes.trim()
    }

    private fun ResultRow.toSupplier(): Supplier =
        Supplier(
            identifier = this[SupplierTable.id].value,
            name = this[SupplierTable.name],
            contactName = this[SupplierTable.contactName],
            phone = this[SupplierTable.phone],
            email = this[SupplierTable.email],
            website = this[SupplierTable.website],
            accountNumber = this[SupplierTable.accountNumber],
            notes = this[SupplierTable.notes]
        )

    // ------------------------------------------ Orders ------------------------------------------

    fun order(identifier: Uuid): PurchaseOrder? =
        dbQuery(ORDER_TAG) { PurchaseOrderTable.selectAll().where { PurchaseOrderTable.id eq identifier }.singleOrNull()?.toOrder() }

    /** Newest first, only [statuses] when given, only the page's location's when it names one. Null when the read failed */
    fun orders(request: PagedRequest, statuses: List<PurchaseStatus>? = null): List<PurchaseOrder>? =
        dbQuery(LIST_TAG) {
            val query = PurchaseOrderTable.selectAll().where {
                val wanted = statuses?.let { list -> PurchaseOrderTable.status inList list.map { it.name } } ?: Op.TRUE
                wanted and PurchaseOrderTable.location.at(request.location)
            }
            query
                .orderBy(PurchaseOrderTable.numberYear to SortOrder.DESC, PurchaseOrderTable.numberSequence to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toOrder() }
        }

    /** Every order that isn't finished, for finding a ticket's parts or a supplier's draft. Null when the read failed */
    fun unfinished(): List<PurchaseOrder>? =
        dbQuery(LIST_TAG) {
            PurchaseOrderTable.selectAll()
                .where { PurchaseOrderTable.status inList listOf(PurchaseStatus.DRAFT, PurchaseStatus.ORDERED, PurchaseStatus.PARTLY_RECEIVED).map { it.name } }
                .orderBy(PurchaseOrderTable.numberSequence to SortOrder.ASC)
                .map { it.toOrder() }
        }

    /** Orders with a line for the ticket, newest first. JSON lines are searched here, the table stays small */
    fun forTicket(ticketIdentifier: Uuid): List<PurchaseOrder>? =
        dbQuery(LIST_TAG) {
            PurchaseOrderTable.selectAll()
                .orderBy(PurchaseOrderTable.numberYear to SortOrder.DESC, PurchaseOrderTable.numberSequence to SortOrder.DESC)
                .map { it.toOrder() }
                .filter { order -> order.lines.any { it.ticketIdentifier == ticketIdentifier } }
        }

    /** Saves a new order with the next number at its location. Called holding the purchase service's lock */
    fun add(order: PurchaseOrder): PurchaseOrder? =
        dbQuery(ORDER_TAG) {
            val year = currentYear()
            val prefix = numberPrefix(order.locationIdentifier)
            val max = PurchaseOrderTable.numberSequence.max()
            val sequence = (PurchaseOrderTable.select(max).where { (PurchaseOrderTable.numberYear eq year) and (PurchaseOrderTable.numberPrefix eq prefix) }.singleOrNull()?.get(max) ?: 0L) + 1

            PurchaseOrderTable.insert { row ->
                row[PurchaseOrderTable.id] = order.identifier
                row[numberYear] = year
                row[numberPrefix] = prefix
                row[location] = order.locationIdentifier
                row[numberSequence] = sequence
                row[supplier] = order.supplierIdentifier
                row[supplierName] = order.supplierName
                row[status] = order.status.name
                row[lines] = order.lines
                row[note] = order.note
                row[createdAt] = order.createdAt
                row[createdBy] = order.createdBy
                row[expectedAt] = order.expectedAt
            }

            PurchaseOrderTable.selectAll().where { PurchaseOrderTable.id eq order.identifier }.single().toOrder()
        }

    /** Saves everything but the number and when it was made */
    fun save(order: PurchaseOrder): Boolean =
        dbWrite(ORDER_TAG) {
            PurchaseOrderTable.update(where = { PurchaseOrderTable.id eq order.identifier }) { row ->
                row[supplier] = order.supplierIdentifier
                row[supplierName] = order.supplierName
                row[status] = order.status.name
                row[lines] = order.lines
                row[note] = order.note
                row[orderedAt] = order.orderedAt
                row[receivedAt] = order.receivedAt
                row[expectedAt] = order.expectedAt
            } > 0
        }

    fun remove(identifier: Uuid): Boolean =
        dbWrite(ORDER_TAG) { PurchaseOrderTable.deleteWhere { PurchaseOrderTable.id eq identifier } > 0 }

    private fun ResultRow.toOrder(): PurchaseOrder =
        PurchaseOrder(
            identifier = this[PurchaseOrderTable.id].value,
            number = PurchaseNumber(this[PurchaseOrderTable.numberYear], this[PurchaseOrderTable.numberSequence], this[PurchaseOrderTable.numberPrefix]),
            locationIdentifier = this[PurchaseOrderTable.location],
            supplierIdentifier = this[PurchaseOrderTable.supplier],
            supplierName = this[PurchaseOrderTable.supplierName],
            status = PurchaseStatus.valueOf(this[PurchaseOrderTable.status]),
            lines = this[PurchaseOrderTable.lines],
            note = this[PurchaseOrderTable.note],
            createdAt = this[PurchaseOrderTable.createdAt],
            createdBy = this[PurchaseOrderTable.createdBy],
            orderedAt = this[PurchaseOrderTable.orderedAt],
            receivedAt = this[PurchaseOrderTable.receivedAt],
            expectedAt = this[PurchaseOrderTable.expectedAt]
        )
}

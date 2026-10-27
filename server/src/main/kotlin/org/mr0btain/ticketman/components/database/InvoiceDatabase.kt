package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceChargeTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoicePaymentTable
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceTable
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shared.PagedRequest
import java.time.Year
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Invoices with their charges and payments. Doesn't check rules like only changing open invoices,
 * that's up to the invoice service
 * @param currentYear Year new invoice numbers are made in
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class InvoiceDatabase(
    private val currentYear: () -> Int = { Year.now().value }
) {
    private val TAG = "InvoiceDatabase"
    private val GET_TAG = "$TAG:Get"
    private val CREATE_TAG = "$TAG:Create"
    private val SET_CHARGES_TAG = "$TAG:SetCharges"
    private val PAYMENT_TAG = "$TAG:Payment"
    private val STATUS_TAG = "$TAG:Status"
    private val PAGED_TAG = "$TAG:Paged"
    private val RANGE_TAG = "$TAG:Range"
    private val REMOVE_TAG = "$TAG:Remove"
    private val COST_TAG = "$TAG:Cost"

    fun getById(identifier: Uuid): Invoice? =
        dbQuery(GET_TAG) {
            InvoiceTable.selectAll().where { InvoiceTable.id eq identifier }.firstOrNull()?.toInvoice()
        }

    fun getByTicket(ticketIdentifier: Uuid): Invoice? =
        dbQuery(GET_TAG) {
            InvoiceTable.selectAll().where { InvoiceTable.ticket eq ticketIdentifier }.firstOrNull()?.toInvoice()
        }

    /**
     * Runs [block] holding the numbering lock, for an outer transaction that makes an invoice, see
     * [TicketDatabase.whileNumbering]
     */
    fun <T> whileNumbering(block: () -> T): T = synchronized(NUMBER_LOCK, block)

    /**
     * The ticket's invoice, made empty and OPEN at the ticket's location if it doesn't have one yet
     */
    fun getOrCreate(ticketIdentifier: Uuid, customerIdentifier: Uuid?, taxRateBasisPoints: Int, taxRates: List<TaxRate> = emptyList()): Invoice? =
        // Numbers are handed out one at a time like ticket numbers
        synchronized(NUMBER_LOCK) {
            dbQuery(CREATE_TAG) {
                InvoiceTable.selectAll().where { InvoiceTable.ticket eq ticketIdentifier }.firstOrNull()?.let {
                    return@dbQuery it.toInvoice()
                }

                val location = TicketTable.select(TicketTable.location).where { TicketTable.id eq ticketIdentifier }.firstOrNull()?.get(TicketTable.location)
                insert(ticketIdentifier, customerIdentifier, taxRateBasisPoints, taxRates, location)
            }
        }

    /** A new empty OPEN invoice with no ticket at [location], for a counter sale. Called inside the sale's transaction */
    fun createSale(customerIdentifier: Uuid?, taxRateBasisPoints: Int, taxRates: List<TaxRate>, location: Uuid? = null): Invoice? =
        synchronized(NUMBER_LOCK) {
            dbQuery(CREATE_TAG) { insert(null, customerIdentifier, taxRateBasisPoints, taxRates, location) }
        }

    // Must be called inside a transaction, holding the numbering lock. Numbered at its location
    private fun insert(ticketIdentifier: Uuid?, customerIdentifier: Uuid?, taxRateBasisPoints: Int, taxRates: List<TaxRate>, location: Uuid?): Invoice {
        val year = currentYear()
        val prefix = numberPrefix(location)
        val max = InvoiceTable.numberSequence.max()
        val sequence = (InvoiceTable.select(max).where { (InvoiceTable.numberYear eq year) and (InvoiceTable.numberPrefix eq prefix) }.singleOrNull()?.get(max) ?: 0L) + 1
        val identifier = Uuid.generateV7()

        InvoiceTable.insert { row ->
            row[InvoiceTable.id] = identifier
            row[InvoiceTable.numberYear] = year
            row[InvoiceTable.numberPrefix] = prefix
            row[InvoiceTable.location] = location
            row[InvoiceTable.numberSequence] = sequence
            row[InvoiceTable.ticket] = ticketIdentifier
            row[InvoiceTable.customer] = customerIdentifier?.toString()
            row[InvoiceTable.status] = InvoiceStatus.OPEN.name
            row[InvoiceTable.taxRateBasisPoints] = taxRateBasisPoints
            row[InvoiceTable.taxRates] = taxRates
            row[InvoiceTable.createdAt] = Clock.System.now().toEpochMilliseconds()
        }

        return InvoiceTable.selectAll().where { InvoiceTable.id eq identifier }.single().toInvoice()
    }

    /**
     * Replaces the charges and discount and saves the worked out totals
     * @param taxRates The tax classes to keep on it, null for the ones it has
     */
    fun setCharges(identifier: Uuid, charges: List<Charge>, discountCents: Long, taxRateBasisPoints: Int, taxRates: List<TaxRate>? = null): Invoice? =
        dbQuery(SET_CHARGES_TAG) {
            val rates = taxRates ?: InvoiceTable.select(InvoiceTable.taxRates).where { InvoiceTable.id eq identifier }.singleOrNull()?.get(InvoiceTable.taxRates).orEmpty()

            InvoiceChargeTable.deleteWhere { InvoiceChargeTable.invoice eq identifier }

            charges.forEachIndexed { index, charge ->
                InvoiceChargeTable.insert { row ->
                    row[InvoiceChargeTable.id] = charge.identifier
                    row[InvoiceChargeTable.invoice] = identifier
                    row[InvoiceChargeTable.position] = index
                    row[InvoiceChargeTable.type] = charge.type.name
                    row[InvoiceChargeTable.description] = charge.description
                    row[InvoiceChargeTable.quantity] = charge.quantity
                    row[InvoiceChargeTable.unitPriceCents] = charge.unitPriceCents
                    row[InvoiceChargeTable.inventory] = charge.inventoryIdentifier?.toString()
                    row[InvoiceChargeTable.taxable] = charge.taxable
                    row[InvoiceChargeTable.reference] = charge.reference
                    row[InvoiceChargeTable.taxClass] = charge.taxClass
                    row[InvoiceChargeTable.warrantyDays] = charge.warrantyDays
                }
            }

            val totals = InvoiceMath.totals(charges, discountCents, taxRateBasisPoints, rates)

            InvoiceTable.update(where = { InvoiceTable.id eq identifier }) { row ->
                row[InvoiceTable.discountCents] = totals.discountCents
                row[InvoiceTable.taxRateBasisPoints] = taxRateBasisPoints
                row[InvoiceTable.taxRates] = rates
                row[subtotalCents] = totals.subtotalCents
                row[taxCents] = totals.taxCents
                row[totalCents] = totals.totalCents
            }

            InvoiceTable.selectAll().where { InvoiceTable.id eq identifier }.firstOrNull()?.toInvoice()
        }

    fun addPayment(identifier: Uuid, payment: Payment): Invoice? =
        dbQuery(PAYMENT_TAG) {
            InvoicePaymentTable.insert { row ->
                row[InvoicePaymentTable.id] = payment.identifier
                row[InvoicePaymentTable.invoice] = identifier
                row[InvoicePaymentTable.method] = payment.method.name
                row[InvoicePaymentTable.amountCents] = payment.amountCents
                row[InvoicePaymentTable.receivedAt] = payment.receivedAt
                row[InvoicePaymentTable.receivedBy] = payment.receivedBy
                row[InvoicePaymentTable.note] = payment.note
                row[InvoicePaymentTable.returns] = payment.returns
                row[InvoicePaymentTable.reference] = payment.reference
                row[InvoicePaymentTable.processor] = payment.processor?.name
                row[InvoicePaymentTable.processorReference] = payment.processorReference
                row[InvoicePaymentTable.charge] = payment.chargeIdentifier
                row[InvoicePaymentTable.refundOf] = payment.refundOf
                row[InvoicePaymentTable.tipCents] = payment.tipCents
                row[InvoicePaymentTable.surchargeCents] = payment.surchargeCents
            }

            InvoiceTable.selectAll().where { InvoiceTable.id eq identifier }.firstOrNull()?.toInvoice()
        }

    /** @return Whether the payment was on this invoice */
    fun removePayment(identifier: Uuid, paymentIdentifier: Uuid): Boolean =
        dbWrite(PAYMENT_TAG) {
            InvoicePaymentTable.deleteWhere {
                (InvoicePaymentTable.id eq paymentIdentifier) and (InvoicePaymentTable.invoice eq identifier)
            } > 0
        }

    /** Deletes the invoice with its lines and payments */
    fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) { InvoiceTable.deleteWhere { InvoiceTable.id eq identifier } > 0 }

    /** Staff took the storage line off, so syncing doesn't put it back */
    fun setStorageWaived(identifier: Uuid): Boolean =
        dbWrite(STATUS_TAG) { InvoiceTable.update(where = { InvoiceTable.id eq identifier }) { row -> row[storageWaived] = true } > 0 }

    fun setStatus(identifier: Uuid, status: InvoiceStatus, paidAt: Long? = null): Boolean =
        dbWrite(STATUS_TAG) {
            InvoiceTable.update(where = { InvoiceTable.id eq identifier }) { row ->
                row[InvoiceTable.status] = status.name
                if (paidAt != null) row[InvoiceTable.paidAt] = paidAt
            } > 0
        }

    /**
     * Invoices made from [fromMillis] up to but not including [toMillis], oldest number first, for the export.
     * Null when the read failed
     */
    fun getCreatedBetween(fromMillis: Long, toMillis: Long, location: Uuid? = null): List<Invoice>? =
        dbQuery(RANGE_TAG) {
            InvoiceTable
                .selectAll()
                .where { (InvoiceTable.createdAt greaterEq fromMillis) and (InvoiceTable.createdAt less toMillis) and at(location) }
                .orderBy(InvoiceTable.numberYear to SortOrder.ASC, InvoiceTable.numberSequence to SortOrder.ASC)
                .map { it.toInvoice() }
        }

    /**
     * Invoices paid from [fromMillis] up to but not including [toMillis], oldest payment first, for the reports.
     * Null when the read failed
     */
    fun getPaidBetween(fromMillis: Long, toMillis: Long, location: Uuid? = null): List<Invoice>? =
        dbQuery(RANGE_TAG) {
            InvoiceTable
                .selectAll()
                .where {
                    (InvoiceTable.status eq InvoiceStatus.PAID.name) and
                        (InvoiceTable.paidAt greaterEq fromMillis) and (InvoiceTable.paidAt less toMillis) and at(location)
                }
                .orderBy(InvoiceTable.paidAt to SortOrder.ASC)
                .map { it.toInvoice() }
        }

    /**
     * Invoices with a payment or refund received from [fromMillis] up to but not including [toMillis], for the
     * payments export. Null when the read failed
     */
    fun withPaymentsBetween(fromMillis: Long, toMillis: Long, location: Uuid? = null): List<Invoice>? =
        dbQuery(RANGE_TAG) {
            val ids = InvoicePaymentTable
                .select(InvoicePaymentTable.invoice)
                .where { (InvoicePaymentTable.receivedAt greaterEq fromMillis) and (InvoicePaymentTable.receivedAt less toMillis) }
                .map { it[InvoicePaymentTable.invoice].value }
                .distinct()
            if (ids.isEmpty()) emptyList() else ids.chunked(COST_CHUNK).flatMap { chunk ->
                InvoiceTable.selectAll().where { (InvoiceTable.id inList chunk) and at(location) }.map { it.toInvoice() }
            }
        }

    /**
     * Keeps what each part on the invoice costs now on its line, for the profit report. Called in the transaction
     * that pays it, before the invoice can't change any more
     */
    fun recordCosts(identifier: Uuid): Boolean =
        dbWrite(COST_TAG) {
            InvoiceChargeTable
                .select(InvoiceChargeTable.id, InvoiceChargeTable.inventory)
                .where { (InvoiceChargeTable.invoice eq identifier) and InvoiceChargeTable.inventory.isNotNull() }
                .toList()
                .forEach { row ->
                    val item = row[InvoiceChargeTable.inventory]?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: return@forEach
                    val cost = InventoryTable.select(InventoryTable.costCents).where { InventoryTable.id eq item }.singleOrNull()?.get(InventoryTable.costCents)
                    InvoiceChargeTable.update(where = { InvoiceChargeTable.id eq row[InvoiceChargeTable.id] }) { it[costCents] = cost }
                }
            true
        }

    /** Each part line's kept cost, by line, for the lines on [invoices] that have one. Null when the read failed */
    fun costs(invoices: List<Uuid>): Map<Uuid, Long>? =
        dbQuery(COST_TAG) {
            invoices.chunked(COST_CHUNK).flatMap { chunk ->
                InvoiceChargeTable
                    .select(InvoiceChargeTable.id, InvoiceChargeTable.costCents)
                    .where { (InvoiceChargeTable.invoice inList chunk) and InvoiceChargeTable.costCents.isNotNull() }
                    .map { it[InvoiceChargeTable.id].value to it[InvoiceChargeTable.costCents]!! }
            }.toMap()
        }

    /** The customer's invoices, newest first. Null when the read failed */
    fun getByCustomer(customerIdentifier: Uuid, limit: Int): List<Invoice>? =
        dbQuery(GET_TAG) {
            InvoiceTable
                .selectAll()
                .where { InvoiceTable.customer eq customerIdentifier.toString() }
                .orderBy(InvoiceTable.numberYear to SortOrder.DESC, InvoiceTable.numberSequence to SortOrder.DESC)
                .limit(limit)
                .map { it.toInvoice() }
        }

    /** Newest first, only the page's location's when it names one */
    fun getPagedItems(request: PagedRequest): List<Invoice> =
        dbQuery(PAGED_TAG) {
            InvoiceTable
                .selectAll()
                .where { InvoiceTable.location.at(request.location) }
                .orderBy(InvoiceTable.numberYear to SortOrder.DESC, InvoiceTable.numberSequence to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toInvoice() }
        }.orEmpty()

    // Only [location]'s invoices, every one's for null
    private fun at(location: Uuid?): Op<Boolean> = InvoiceTable.location.at(location)

    // Must be called inside a transaction
    private fun ResultRow.toInvoice(): Invoice {
        val identifier = this[InvoiceTable.id].value

        val charges = InvoiceChargeTable
            .selectAll()
            .where { InvoiceChargeTable.invoice eq identifier }
            .orderBy(InvoiceChargeTable.position to SortOrder.ASC)
            .map { row ->
                Charge(
                    identifier = row[InvoiceChargeTable.id].value,
                    type = ChargeType.valueOf(row[InvoiceChargeTable.type]),
                    description = row[InvoiceChargeTable.description],
                    quantity = row[InvoiceChargeTable.quantity],
                    unitPriceCents = row[InvoiceChargeTable.unitPriceCents],
                    inventoryIdentifier = row[InvoiceChargeTable.inventory]?.let { Uuid.parse(it) },
                    taxable = row[InvoiceChargeTable.taxable],
                    reference = row[InvoiceChargeTable.reference],
                    taxClass = row[InvoiceChargeTable.taxClass],
                    warrantyDays = row[InvoiceChargeTable.warrantyDays]
                )
            }

        val payments = InvoicePaymentTable
            .selectAll()
            .where { InvoicePaymentTable.invoice eq identifier }
            .orderBy(InvoicePaymentTable.receivedAt to SortOrder.ASC)
            .map { row ->
                Payment(
                    identifier = row[InvoicePaymentTable.id].value,
                    method = PaymentMethod.valueOf(row[InvoicePaymentTable.method]),
                    amountCents = row[InvoicePaymentTable.amountCents],
                    receivedAt = row[InvoicePaymentTable.receivedAt],
                    receivedBy = row[InvoicePaymentTable.receivedBy],
                    note = row[InvoicePaymentTable.note],
                    returns = row[InvoicePaymentTable.returns],
                    reference = row[InvoicePaymentTable.reference],
                    processor = row[InvoicePaymentTable.processor]?.let { name -> PaymentProvider.entries.firstOrNull { it.name == name } },
                    processorReference = row[InvoicePaymentTable.processorReference],
                    chargeIdentifier = row[InvoicePaymentTable.charge],
                    refundOf = row[InvoicePaymentTable.refundOf],
                    tipCents = row[InvoicePaymentTable.tipCents],
                    surchargeCents = row[InvoicePaymentTable.surchargeCents]
                )
            }

        return Invoice(
            identifier = identifier,
            number = InvoiceNumber(this[InvoiceTable.numberYear], this[InvoiceTable.numberSequence], this[InvoiceTable.numberPrefix]),
            locationIdentifier = this[InvoiceTable.location],
            ticketIdentifier = this[InvoiceTable.ticket],
            customerIdentifier = this[InvoiceTable.customer]?.let { Uuid.parse(it) },
            status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
            charges = charges,
            payments = payments,
            discountCents = this[InvoiceTable.discountCents],
            taxRateBasisPoints = this[InvoiceTable.taxRateBasisPoints],
            taxRates = this[InvoiceTable.taxRates],
            storageWaived = this[InvoiceTable.storageWaived],
            subtotalCents = this[InvoiceTable.subtotalCents],
            taxCents = this[InvoiceTable.taxCents],
            totalCents = this[InvoiceTable.totalCents],
            createdAt = this[InvoiceTable.createdAt],
            paidAt = this[InvoiceTable.paidAt],
            ticketLabel = this[InvoiceTable.ticketLabel],
            ticketRemovedAt = this[InvoiceTable.ticketRemovedAt]
        )
    }

    companion object {
        private val NUMBER_LOCK = Any()
        private const val COST_CHUNK = 500
    }
}

package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceChargeTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoicePaymentTable
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

    fun getById(identifier: Uuid): Invoice? =
        dbQuery(GET_TAG) {
            InvoiceTable.selectAll().where { InvoiceTable.id eq identifier }.firstOrNull()?.toInvoice()
        }

    fun getByTicket(ticketIdentifier: Uuid): Invoice? =
        dbQuery(GET_TAG) {
            InvoiceTable.selectAll().where { InvoiceTable.ticket eq ticketIdentifier }.firstOrNull()?.toInvoice()
        }

    /**
     * The ticket's invoice, made empty and OPEN if it doesn't have one yet
     */
    fun getOrCreate(ticketIdentifier: Uuid, customerIdentifier: Uuid?, taxRateBasisPoints: Int): Invoice? =
        // Numbers are handed out one at a time like ticket numbers
        synchronized(NUMBER_LOCK) {
            dbQuery(CREATE_TAG) {
                InvoiceTable.selectAll().where { InvoiceTable.ticket eq ticketIdentifier }.firstOrNull()?.let {
                    return@dbQuery it.toInvoice()
                }

                val year = currentYear()
                val max = InvoiceTable.numberSequence.max()
                val sequence = (InvoiceTable.select(max).where { InvoiceTable.numberYear eq year }.singleOrNull()?.get(max) ?: 0L) + 1
                val identifier = Uuid.generateV7()

                InvoiceTable.insert { row ->
                    row[InvoiceTable.id] = identifier
                    row[InvoiceTable.numberYear] = year
                    row[InvoiceTable.numberSequence] = sequence
                    row[InvoiceTable.ticket] = ticketIdentifier
                    row[InvoiceTable.customer] = customerIdentifier?.toString()
                    row[InvoiceTable.status] = InvoiceStatus.OPEN.name
                    row[InvoiceTable.taxRateBasisPoints] = taxRateBasisPoints
                    row[InvoiceTable.createdAt] = Clock.System.now().toEpochMilliseconds()
                }

                InvoiceTable.selectAll().where { InvoiceTable.id eq identifier }.single().toInvoice()
            }
        }

    /** Replaces the charges and discount and saves the worked out totals */
    fun setCharges(identifier: Uuid, charges: List<Charge>, discountCents: Long, taxRateBasisPoints: Int): Invoice? =
        dbQuery(SET_CHARGES_TAG) {
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
                }
            }

            val totals = InvoiceMath.totals(charges, discountCents, taxRateBasisPoints)

            InvoiceTable.update(where = { InvoiceTable.id eq identifier }) { row ->
                row[InvoiceTable.discountCents] = totals.discountCents
                row[InvoiceTable.taxRateBasisPoints] = taxRateBasisPoints
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

    fun setStatus(identifier: Uuid, status: InvoiceStatus, paidAt: Long? = null): Boolean =
        dbWrite(STATUS_TAG) {
            InvoiceTable.update(where = { InvoiceTable.id eq identifier }) { row ->
                row[InvoiceTable.status] = status.name
                if (paidAt != null) row[InvoiceTable.paidAt] = paidAt
            } > 0
        }

    /** Newest first */
    fun getPagedItems(request: PagedRequest): List<Invoice> =
        dbQuery(PAGED_TAG) {
            InvoiceTable
                .selectAll()
                .orderBy(InvoiceTable.numberYear to SortOrder.DESC, InvoiceTable.numberSequence to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toInvoice() }
        }.orEmpty()

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
                    taxable = row[InvoiceChargeTable.taxable]
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
                    note = row[InvoicePaymentTable.note]
                )
            }

        return Invoice(
            identifier = identifier,
            number = InvoiceNumber(this[InvoiceTable.numberYear], this[InvoiceTable.numberSequence]),
            ticketIdentifier = this[InvoiceTable.ticket].value,
            customerIdentifier = this[InvoiceTable.customer]?.let { Uuid.parse(it) },
            status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
            charges = charges,
            payments = payments,
            discountCents = this[InvoiceTable.discountCents],
            taxRateBasisPoints = this[InvoiceTable.taxRateBasisPoints],
            subtotalCents = this[InvoiceTable.subtotalCents],
            taxCents = this[InvoiceTable.taxCents],
            totalCents = this[InvoiceTable.totalCents],
            createdAt = this[InvoiceTable.createdAt],
            paidAt = this[InvoiceTable.paidAt]
        )
    }

    companion object {
        private val NUMBER_LOCK = Any()
    }
}

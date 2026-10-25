package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A ticket's bill, or a sale at the counter. There's one per ticket, it stays OPEN while being built and taking
 * deposits, and is PAID once the ticket is picked up. A sale has no ticket and is made already PAID (see
 * [isSale]). Totals are worked out by the server
 * @param taxRateBasisPoints Shop tax rate when the invoice was made, kept after, 750 is 7.5%
 * @param taxRates The shop's tax classes when the invoice was made, and any its lines used that were made after
 * @param createdAt Epoch millis
 * @param paidAt Epoch millis it was paid off at pickup
 * @param ticketIdentifier Its ticket, null for a sale straight from stock
 * @param ticketLabel The ticket's number, kept once the ticket is deleted, blank before
 * @param ticketRemovedAt Epoch millis its ticket was deleted. An invoice with payments, or a void one, outlives its
 * ticket, so the money taken stays on record until an admin deletes it
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Invoice(
    override val identifier: Uuid = Uuid.generateV7(),
    val number: InvoiceNumber? = null,
    val ticketIdentifier: Uuid? = null,
    val customerIdentifier: Uuid? = null,
    val status: InvoiceStatus = InvoiceStatus.OPEN,
    val charges: List<Charge> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val discountCents: Long = 0,
    val taxRateBasisPoints: Int = 0,
    val taxRates: List<TaxRate> = emptyList(),
    val subtotalCents: Long = 0,
    val taxCents: Long = 0,
    val totalCents: Long = 0,
    val createdAt: Long = 0,
    val paidAt: Long? = null,
    val ticketLabel: String = "",
    val ticketRemovedAt: Long? = null
): MainItem() {
    /** Sold over the counter without a ticket */
    val isSale: Boolean
        get() = ticketIdentifier == null

    /** Its ticket was deleted, the invoice was kept */
    val ticketRemoved: Boolean
        get() = ticketRemovedAt != null

    /** Whether deleting its ticket keeps it: it has money against it, or was voided */
    val outlivesTicket: Boolean
        get() = payments.isNotEmpty() || status != InvoiceStatus.OPEN

    /** Money taken, not counting refunds */
    val paidCents: Long
        get() = payments.filter { !it.isRefund }.sumOf { it.amountCents }

    /** Money given back, as a positive amount */
    val refundedCents: Long
        get() = -payments.filter { it.isRefund }.sumOf { it.amountCents }

    /** Paid less refunded, what the shop kept */
    val netPaidCents: Long
        get() = paidCents - refundedCents

    /** What's still owed, negative means change is due. Refunds don't make anything owed */
    val balanceCents: Long
        get() = totalCents - paidCents

    /**
     * How many of each item have been returned to stock by refunds, so the same part isn't returned twice
     */
    val returnedParts: Map<Uuid, Int>
        get() = payments.flatMap { it.returns }.groupBy({ it.inventoryIdentifier }, { it.quantity }).mapValues { (_, counts) -> counts.sum() }

    /** How many of each stocked item the part lines sold, added up when one is on several lines */
    val partsSold: Map<Uuid, Int>
        get() = charges
            .filter { it.type == ChargeType.PART && it.inventoryIdentifier != null }
            .groupBy { it.inventoryIdentifier!! }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity.roundToLong() }.toInt() }

    val label: String
        get() = number?.toString() ?: identifier.toString().substringBefore("-")

    /** Its totals worked out again, for the tax on each rate */
    val totals: InvoiceMath.Totals
        get() = InvoiceMath.totals(charges, discountCents, taxRateBasisPoints, taxRates)
}

@Serializable
enum class InvoiceStatus {
    OPEN,
    PAID,
    VOID
}

/**
 * Readable invoice number like INV-26-00000042, counted per year like ticket numbers
 */
@Serializable
data class InvoiceNumber(
    val year: Int,
    val sequence: Long
) {
    override fun toString(): String =
        "INV-${(year % 100).toString().padStart(2, '0')}-${sequence.toString().padStart(8, '0')}"
}

/**
 * Money taken against an invoice, a deposit or the final payment, or given back by a refund
 * @param amountCents Negative for a refund
 * @param receivedAt Epoch millis
 * @param receivedBy Username of whoever took it or gave it back
 * @param note For a refund, the reason
 * @param returns Parts a refund put back in stock
 * @param reference The gift card's code for [PaymentMethod.GIFT_CARD]
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Payment(
    val identifier: Uuid = Uuid.generateV7(),
    val method: PaymentMethod,
    val amountCents: Long,
    val receivedAt: Long = 0,
    val receivedBy: String = "",
    val note: String = "",
    val returns: List<ReturnedPart> = emptyList(),
    val reference: String = ""
) {
    val isRefund: Boolean
        get() = amountCents < 0
}

/** Some of a part taken back by a refund and put back in stock */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class ReturnedPart(
    val inventoryIdentifier: Uuid,
    val quantity: Int
)

/**
 * How a payment was made
 * @param storedValue Spends a balance the shop holds (store credit or a gift card) rather than taking money
 */
@Serializable
enum class PaymentMethod(val storedValue: Boolean = false) {
    CASH,
    CARD,
    CHECK,
    OTHER,

    /** The invoice's customer's store credit */
    STORE_CREDIT(storedValue = true),

    /** A gift card, its code in [Payment.reference] */
    GIFT_CARD(storedValue = true)
}

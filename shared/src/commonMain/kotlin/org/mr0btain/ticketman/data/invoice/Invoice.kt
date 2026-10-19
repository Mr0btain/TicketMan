package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A ticket's bill. There's one per ticket, it stays OPEN while being built and taking deposits,
 * and is PAID once the ticket is picked up. Totals are worked out by the server
 * @param taxRateBasisPoints Shop tax rate when the invoice was made, kept after, 750 is 7.5%
 * @param createdAt Epoch millis
 * @param paidAt Epoch millis it was paid off at pickup
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Invoice(
    override val identifier: Uuid = Uuid.generateV7(),
    val number: InvoiceNumber? = null,
    val ticketIdentifier: Uuid,
    val customerIdentifier: Uuid? = null,
    val status: InvoiceStatus = InvoiceStatus.OPEN,
    val charges: List<Charge> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val discountCents: Long = 0,
    val taxRateBasisPoints: Int = 0,
    val subtotalCents: Long = 0,
    val taxCents: Long = 0,
    val totalCents: Long = 0,
    val createdAt: Long = 0,
    val paidAt: Long? = null
): MainItem() {
    val paidCents: Long
        get() = payments.sumOf { it.amountCents }

    /** What's still owed, negative means change is due */
    val balanceCents: Long
        get() = totalCents - paidCents

    val label: String
        get() = number?.toString() ?: identifier.toString().substringBefore("-")
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
 * Money taken against an invoice, a deposit or the final payment
 * @param receivedAt Epoch millis
 * @param receivedBy Username of whoever took it
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Payment(
    val identifier: Uuid = Uuid.generateV7(),
    val method: PaymentMethod,
    val amountCents: Long,
    val receivedAt: Long = 0,
    val receivedBy: String = "",
    val note: String = ""
)

@Serializable
enum class PaymentMethod {
    CASH,
    CARD,
    CHECK,
    OTHER
}

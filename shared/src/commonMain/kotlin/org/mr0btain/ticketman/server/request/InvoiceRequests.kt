package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import kotlin.uuid.Uuid

/** Replaces an open invoice's lines and discount, totals are worked out by the server */
@Serializable
data class InvoiceUpdateRequest(
    val charges: List<Charge>,
    val discountCents: Long = 0
)

@Serializable
data class PaymentRequest(
    val method: PaymentMethod,
    val amountCents: Long,
    val note: String = ""
)

/**
 * Hands a ticket back: takes the payments, pays off the invoice, takes parts out of stock and closes the ticket
 * @param payments Whatever is being paid now, on top of any deposits
 */
@Serializable
data class PickupRequest(
    val ticketIdentifier: Uuid,
    val payments: List<PaymentRequest> = emptyList()
)

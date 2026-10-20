package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.SignatureInput
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.WorkType
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
 * Checks a device in, all at once so a failure can't leave a ticket half made
 * @param ticket The new ticket, its number is given by the server
 * @param technicianIdentifier Who it's assigned to, if anyone yet
 * @param deposit Money taken now, goes on the ticket's invoice
 */
@Serializable
data class CheckInRequest(
    val ticket: Ticket,
    val customerIdentifier: Uuid,
    val technicianIdentifier: Uuid? = null,
    val deposit: PaymentRequest? = null,
    val signature: SignatureInput? = null
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

/**
 * Logs work on a ticket
 * @param description What was done. For a part, blank means the item's name
 * @param quantity Hours for labor, how many for a part
 * @param unitPriceCents Null for the shop's labor rate, or the item's price for a part
 * @param inventoryIdentifier The item a part came from, needed for parts
 */
@Serializable
data class WorkRequest(
    val type: WorkType,
    val description: String = "",
    val quantity: Double,
    val unitPriceCents: Long? = null,
    val inventoryIdentifier: Uuid? = null
)

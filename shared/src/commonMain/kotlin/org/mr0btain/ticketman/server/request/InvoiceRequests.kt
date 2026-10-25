package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.invoice.ReturnedPart
import org.mr0btain.ticketman.data.ticket.SignatureInput
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.WorkType
import kotlin.uuid.Uuid

/**
 * What someone changed on an open invoice since they opened it, so the server applies just that to the lines it has
 * now and lines added meanwhile (like logged work) are kept. Built with [org.mr0btain.ticketman.data.invoice.InvoiceChanges].
 * Changed and removed lines carry how they were when opened, so one changed by someone else too is refused, not
 * overwritten
 * @param discountCents The new discount, null when it wasn't changed
 * @param discountWas The discount when the invoice was opened, checked like a line's [ChargeChange.before]
 */
@Serializable
data class InvoiceChangeRequest(
    val added: List<Charge> = emptyList(),
    val changed: List<ChargeChange> = emptyList(),
    val removed: List<Charge> = emptyList(),
    val discountCents: Long? = null,
    val discountWas: Long = 0
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty() && discountCents == null
}

/** One line as it was when the invoice was opened, and as it is now */
@Serializable
data class ChargeChange(val before: Charge, val after: Charge)

/**
 * A payment being taken
 * @param reference The gift card's code when it's paid with one
 */
@Serializable
data class PaymentRequest(
    val method: PaymentMethod,
    val amountCents: Long,
    val note: String = "",
    val reference: String = ""
)

/**
 * Checks a device in, all at once so a failure can't leave a ticket half made
 * @param ticket The new ticket, its number is given by the server
 * @param technicianIdentifier Who it's assigned to, if anyone yet
 * @param deposit Money taken now, goes on the ticket's invoice
 * @param deviceIdentifier The customer's device it is, when one they had before was picked. Null makes one, or finds
 * theirs with the same serial
 * @param more Other devices checked in at the same visit: each gets its own ticket, all linked, with the same
 * technician and signature. The deposit goes on the first
 * @param passcode The device's passcode or pattern, blank for none. Kept encrypted, shown only to staff who can edit
 * @param appointmentIdentifier The booked appointment this visit is, marked checked in with the first ticket
 */
@Serializable
data class CheckInRequest(
    val ticket: Ticket,
    val customerIdentifier: Uuid,
    val technicianIdentifier: Uuid? = null,
    val deposit: PaymentRequest? = null,
    val signature: SignatureInput? = null,
    val deviceIdentifier: Uuid? = null,
    val more: List<CheckInItem> = emptyList(),
    val passcode: String = "",
    val appointmentIdentifier: Uuid? = null
)

/** Another device at the same check in */
@Serializable
data class CheckInItem(
    val ticket: Ticket,
    val deviceIdentifier: Uuid? = null,
    val passcode: String = ""
)

/** A device's passcode, being set or shown */
@Serializable
data class PasscodeBody(val passcode: String)

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
 * Gives money back on a paid invoice, admin only
 * @param amountCents How much goes back, positive. Zero when only parts are coming back, like an exchange
 * @param reason Why, required, kept as the refund's note
 * @param returns Parts coming back, put back in stock. Only ones the invoice sold and not already returned
 * @param reference For [PaymentMethod.GIFT_CARD], the card it goes back onto; blank makes a new one.
 * [PaymentMethod.STORE_CREDIT] goes to the invoice's customer
 */
@Serializable
data class RefundRequest(
    val method: PaymentMethod,
    val amountCents: Long,
    val reason: String,
    val returns: List<ReturnedPart> = emptyList(),
    val reference: String = ""
)

/**
 * Changes stored value by hand, admin only: store credit given as a goodwill gesture, or a gift card corrected
 * @param amountCents Added, or negative to take off. A balance can't go below zero
 */
@Serializable
data class BalanceAdjustRequest(
    val amountCents: Long,
    val reason: String
)

/**
 * Sells straight from stock with no ticket: the invoice is made, paid and its parts taken from stock together
 * @param customerIdentifier Who bought it, when the counter knows. Optional
 * @param charges What's sold, at least one line
 * @param payments Have to cover the total, more is change
 */
@Serializable
data class SaleRequest(
    val customerIdentifier: Uuid? = null,
    val charges: List<Charge>,
    val discountCents: Long = 0,
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

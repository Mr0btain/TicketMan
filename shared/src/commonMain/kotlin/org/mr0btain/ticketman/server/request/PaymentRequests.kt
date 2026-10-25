package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Sends an amount to a card reader
 * @param readerIdentifier One of the shop's readers
 * @param amountCents What goes on the invoice. The reader asks for it with [tipCents] and the shop's surcharge
 * added
 * @param tipCents A tip on top, when the shop offers them
 * @param invoiceIdentifier The open invoice it pays, where it lands once the card goes through. Null for a sale or
 * a check in deposit, whose payment names the charge once it's paid
 */
@Serializable
data class ReaderChargeRequest(
    val readerIdentifier: String,
    val amountCents: Long,
    val invoiceIdentifier: Uuid? = null,
    val tipCents: Long = 0
)

/**
 * A checkout page for an open invoice, to send the customer
 * @param amountCents Null for the whole balance
 */
@Serializable
data class PaymentLinkRequest(
    val invoiceIdentifier: Uuid,
    val amountCents: Long? = null
)

/** Why an unclaimed charge is being given back, kept in the audit log */
@Serializable
data class ChargeRefundRequest(val reason: String)

package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * One change to a customer's store credit
 * @param amountCents Added, or negative when spent
 * @param balanceAfterCents What they had after it
 * @param invoiceIdentifier The invoice it was spent on or refunded from, null for an adjustment by hand
 * @param at Epoch millis
 * @param by Username of whoever made the change
 */
@Serializable
data class CreditEntry(
    val identifier: Uuid,
    val amountCents: Long,
    val balanceAfterCents: Long,
    val reason: String,
    val invoiceIdentifier: Uuid? = null,
    val at: Long,
    val by: String
)

package org.mr0btain.ticketman.data.shop

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import kotlin.uuid.Uuid

/**
 * What happens to a device left after it's ready: a fee for each day past [feeAfterDays], and treated as abandoned
 * after [abandonAfterDays]. 0 turns either off
 */
@Serializable
data class StorageTerms(
    val feeAfterDays: Int = 0,
    val dailyFeeCents: Long = 0,
    val abandonAfterDays: Int = 0
) {
    val chargesFee: Boolean
        get() = feeAfterDays > 0 && dailyFeeCents > 0

    /** What's wrong with these terms, or null */
    fun problem(): String? = when {
        feeAfterDays !in 0..MAX_DAYS || abandonAfterDays !in 0..MAX_DAYS -> "Storage days can be 0 to $MAX_DAYS"
        dailyFeeCents < 0 -> "The storage fee can't be negative"
        abandonAfterDays in 1..feeAfterDays -> "Devices can only be abandoned after the storage fee starts"
        else -> null
    }

    companion object {
        const val MAX_DAYS = 3650
    }
}

object Storage {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** The storage line's description, which pickup shows */
    fun description(days: Int): String = if (days == 1) "Storage, 1 day" else "Storage, $days days"

    /** When it became ready for pickup, null while it isn't ready */
    fun readySince(ticket: Ticket): Long? = ticket.status.time.takeIf { ticket.status.status == TicketStatusType.DONE }

    /** Whole days it's been ready at [now], 0 when it isn't */
    fun daysReady(ticket: Ticket, now: Long): Int = readySince(ticket)?.let { ((now - it) / DAY_MILLIS).toInt() }?.coerceAtLeast(0) ?: 0

    /** Days the fee is owed for at [now]: each whole day past the free ones */
    fun feeDays(ticket: Ticket, terms: StorageTerms, now: Long): Int =
        if (!terms.chargesFee) 0 else (daysReady(ticket, now) - terms.feeAfterDays).coerceAtLeast(0)

    /** When the fee starts, null when it doesn't apply */
    fun feeStarts(ticket: Ticket, terms: StorageTerms): Long? =
        readySince(ticket)?.takeIf { terms.chargesFee }?.let { it + terms.feeAfterDays * DAY_MILLIS }

    /**
     * The storage line's id on a ticket's invoice, made from the ticket's so syncing it again finds the same line
     */
    fun lineIdentifier(ticket: Uuid): Uuid = ticket.toLongs { most, least -> Uuid.fromLongs(most xor LINE_SALT, least) }

    /** The terms in words, for the claim slip and the status page. Null when there are none */
    fun termsText(terms: StorageTerms, currencySymbol: String): String? {
        val fee = if (terms.chargesFee) {
            "Devices left more than ${terms.feeAfterDays} days after they're ready are charged ${Money.format(terms.dailyFeeCents, currencySymbol)} a day for storage."
        } else null
        val abandon = if (terms.abandonAfterDays > 0) {
            "Devices not collected within ${terms.abandonAfterDays} days of being ready are considered abandoned."
        } else null

        return listOfNotNull(fee, abandon).joinToString(" ").ifBlank { null }
    }

    private const val LINE_SALT = 0x53544F5241474500L
}

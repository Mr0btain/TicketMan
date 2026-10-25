package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.selectable
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Moves a ticket to a status the shop picked for something that happened to it (an estimate sent, a part ordered),
 * recording the change. Never moves a picked up ticket, and only to a status staff could pick themselves
 * @param now Epoch millis, swapped in tests
 */
class TicketMoves(
    private val tickets: TicketDatabase,
    private val shop: ShopDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /**
     * @param choice Where it goes, null leaves it
     * @return The ticket as it is now, null when it isn't there
     */
    fun move(ticketIdentifier: Uuid, choice: StatusChoice?, changedBy: String): Ticket? {
        // Read now, so a change made since (like an approved price) isn't saved over
        val current = tickets.getById(ticketIdentifier) ?: return null
        if (choice == null || choice == current.status.choice || current.status.status == TicketStatusType.PICKED_UP) return current
        if (choice !in shop.get().statuses.selectable()) return current

        val moved = current.copy(status = choice.toStatus(now()))
        if (!tickets.update(moved)) return current

        tickets.recordStatusChange(current.identifier, moved.status, changedBy)
        return moved
    }
}

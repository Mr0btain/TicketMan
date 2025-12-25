package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.UUID

class TicketCache(maxSize: Int): CustomCache<TicketCache.TicketData>("TicketCache", maxSize) {
    suspend fun put(ticket: Ticket, notes: List<Note> = emptyList()) =
        cache.put(ticket.identifier, TicketData(ticket, notes))

    data class TicketData(
        val ticket: Ticket,
        val notes: List<Note>
    )
}
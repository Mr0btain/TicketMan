package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.UUID

class TicketCache(size: Int) {
    private val cache = LruCache<UUID, TicketData>(size, "TicketCache")

    suspend fun get(identifier: UUID): TicketData? =
        cache.get(identifier)

    suspend fun put(ticket: Ticket, notes: List<Note> = emptyList()) =
        cache.put(ticket.identifier, TicketData(ticket, notes))

    data class TicketData(
        val ticket: Ticket,
        val notes: List<Note>
    )
}
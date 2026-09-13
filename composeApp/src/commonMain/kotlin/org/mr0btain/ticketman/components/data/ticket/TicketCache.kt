package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket

class TicketCache(maxSize: Int): CustomCache<TicketCache.TicketData>(maxSize, "TicketCache") {
    suspend fun put(ticket: Ticket, notes: List<Note> = emptyList(), customer: Identity?, user: Identity?) =
        cache.put(ticket.identifier.toString(), TicketData(ticket, notes, customer, user))

    data class TicketData(
        val ticket: Ticket,
        val notes: List<Note>,
        val customer: Identity?,
        val user: Identity?
    )
}
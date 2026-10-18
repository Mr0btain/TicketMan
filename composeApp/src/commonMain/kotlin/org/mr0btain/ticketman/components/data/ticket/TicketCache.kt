package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket

class TicketCache(maxSize: Int): CustomCache<TicketCache.TicketData>(maxSize) {
    fun put(ticket: Ticket, notes: List<Note> = emptyList(), customer: EntityReference?, user: EntityReference?) =
        cache.put(ticket.identifier.toString(), TicketData(ticket, notes, customer, user))

    data class TicketData(
        val ticket: Ticket,
        val notes: List<Note>,
        val customer: EntityReference?,
        val user: EntityReference?
    )
}
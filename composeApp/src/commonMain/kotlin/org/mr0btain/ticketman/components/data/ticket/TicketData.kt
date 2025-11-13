package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import java.util.UUID

data class TicketData(
    val ticket: Ticket = Ticket(),
    val notes: List<NoteData> = emptyList()
    )
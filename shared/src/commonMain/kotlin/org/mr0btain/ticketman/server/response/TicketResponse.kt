package org.mr0btain.ticketman.server.response

import org.mr0btain.ticketman.data.ticket.TicketInfo

data class TicketResponse(
    val tickets: List<TicketInfo>
)

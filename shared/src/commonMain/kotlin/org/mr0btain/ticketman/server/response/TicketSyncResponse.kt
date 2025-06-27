package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.ticket.TicketInfo

@Serializable
data class TicketSyncResponse(
    val data: List<TicketInfo>,
    val requested: List<Int>
)
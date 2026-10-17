package org.mr0btain.ticketman.data.counter

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.ticket.Ticket

/**
 * A ticket along with who it belongs to and who's working on it, so lists don't need extra lookups
 */
@Serializable
data class TicketSummary(
    val ticket: Ticket,
    val customer: EntityReference? = null,
    val technician: EntityReference? = null
)

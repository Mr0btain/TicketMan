package org.mr0btain.ticketman.components.data.components.ticket

import kotlinx.serialization.Serializable

@Serializable
data class TicketPriority(
    val priority: TicketPriorityType,
    val time: Long = Long.MAX_VALUE
)

@Serializable
enum class TicketPriorityType {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

data class TicketPriority(
    val priority: TicketPriorityType,
    val time: Long
)

@Serializable
enum class TicketPriorityType {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
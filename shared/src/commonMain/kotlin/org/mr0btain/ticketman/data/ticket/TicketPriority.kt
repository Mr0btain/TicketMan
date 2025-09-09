package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class TicketPriority(
    val priority: TicketPriorityType,
    val time: Long = Long.MAX_VALUE
): SubItem

@Serializable
enum class TicketPriorityType {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
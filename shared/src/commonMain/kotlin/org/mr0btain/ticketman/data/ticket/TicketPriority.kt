package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.time.TimeSerializer

@Serializable
data class TicketPriority(
    val priority: TicketPriorityType = TicketPriorityType.NORMAL,
    @Serializable(TimeSerializer::class)
    val time: Time = Time.getTime()
): SubItem

@Serializable
enum class TicketPriorityType {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
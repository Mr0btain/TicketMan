package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.time.Clock

@Serializable
data class TicketPriority(
    val priority: TicketPriorityType = TicketPriorityType.NORMAL,
    val time: Long = Clock.System.now().toEpochMilliseconds(),
): SubItem

@Serializable
enum class TicketPriorityType {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
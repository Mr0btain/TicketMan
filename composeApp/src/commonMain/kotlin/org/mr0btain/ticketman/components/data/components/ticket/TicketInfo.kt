package org.mr0btain.ticketman.components.data.components.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.components.data.components.shared.Note
import org.mr0btain.ticketman.components.data.components.shared.getTime
import kotlin.random.Random

@Serializable
data class TicketInfo(
    val identifier: Int = Int.MAX_VALUE,
    val customerId: Int = Int.MAX_VALUE,
    val assignedEmployee: Int = Int.MAX_VALUE,
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus(TicketStatusType.IN_PROGRESS, Long.MAX_VALUE),
    val priority: TicketPriority = TicketPriority(TicketPriorityType.LOW, Long.MAX_VALUE),
    val updates: List<Note> = listOf(),
    val time: Long = Long.MAX_VALUE
)

fun randomTicketStatus(): TicketStatus {
    val values = TicketStatusType.entries.toTypedArray()

    return TicketStatus(values[Random.nextInt(values.size)], getTime())
}

fun randomTicketPriority(): TicketPriority {
    val values = TicketPriorityType.entries.toTypedArray()

    return TicketPriority(values[Random.nextInt(values.size)], getTime())
}

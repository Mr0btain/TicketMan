package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class TicketInfo(
    val idNum: String = "",
    val customerId: String = "",
    val assignedEmployee: String = "",
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus.DONE,
    val priority: TicketPriority = TicketPriority.NORMAL,
    val updates: List<Note> = listOf()
)

@Serializable
enum class TicketStatus {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}

fun randomTicketStatus(): TicketStatus {
    val values = TicketStatus.entries.toTypedArray()
    return values[Random.nextInt(values.size)]
}

@Serializable
enum class TicketPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

fun randomTicketPriority(): TicketPriority {
    val values = TicketPriority.entries.toTypedArray()
    return values[Random.nextInt(values.size)]
}

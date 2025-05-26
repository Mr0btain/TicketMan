package org.mr0btain.ticketman.components.data

import kotlin.random.Random

data class TicketInfo(
    val id: String = "",
    val customerId: String = "",
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus.DONE,
    val priority: TicketPriority = TicketPriority.NORMAL,
    val notes: List<Note> = listOf()
)

enum class TicketStatus {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}

fun randomTicketStatus(): TicketStatus {
    val values = TicketStatus.entries.toTypedArray()
    return values[Random.nextInt(values.size)]
}

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

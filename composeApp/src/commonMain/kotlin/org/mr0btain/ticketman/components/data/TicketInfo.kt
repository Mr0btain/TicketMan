package org.mr0btain.ticketman.components.data

data class TicketInfo(
    val id: String,
    val device: Device,
    val status: TicketStatus,
    val notes: List<Note>
)

enum class TicketStatus {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}

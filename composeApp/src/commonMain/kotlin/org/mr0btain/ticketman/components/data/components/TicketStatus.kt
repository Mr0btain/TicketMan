package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

data class TicketStatus(
    val status: TicketStatusType,
    val time: Long
)

@Serializable
enum class TicketStatusType {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}
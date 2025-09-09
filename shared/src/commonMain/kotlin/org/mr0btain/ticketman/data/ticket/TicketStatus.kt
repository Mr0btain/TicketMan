package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class TicketStatus(
    val status: TicketStatusType,
    val time: Long = Long.MAX_VALUE
): SubItem

@Serializable
enum class TicketStatusType {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}
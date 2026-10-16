package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.time.Clock

@Serializable
data class TicketStatus(
    val status: TicketStatusType = TicketStatusType.IN_PROGRESS,
    val time: Long = Clock.System.now().toEpochMilliseconds(),
): SubItem

@Serializable
enum class TicketStatusType {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}
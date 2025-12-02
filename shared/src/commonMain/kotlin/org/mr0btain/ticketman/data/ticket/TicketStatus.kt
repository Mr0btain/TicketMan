package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.time.TimeSerializer

@Serializable
data class TicketStatus(
    val status: TicketStatusType = TicketStatusType.IN_PROGRESS,
    @Serializable(TimeSerializer::class)
    val time: Time = Time.getTime()
): SubItem

@Serializable
enum class TicketStatusType {
    IN_PROGRESS,
    ON_HOLD,
    DONE
}
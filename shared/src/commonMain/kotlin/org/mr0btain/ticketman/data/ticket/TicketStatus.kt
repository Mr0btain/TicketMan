package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.time.Clock

@Serializable
data class TicketStatus(
    val status: TicketStatusType = TicketStatusType.IN_PROGRESS,
    val time: Long = Clock.System.now().toEpochMilliseconds(),
): SubItem

/**
 * DONE means the repair is finished and waiting for the customer, PICKED_UP closes the ticket
 */
@Serializable
enum class TicketStatusType {
    IN_PROGRESS,
    ON_HOLD,
    DONE,
    PICKED_UP;

    /** Still being worked on */
    val isOpen: Boolean
        get() = this == IN_PROGRESS || this == ON_HOLD

    companion object {
        /** Statuses that can be picked by hand, picked up is only set by finishing a pickup */
        val selectable = listOf(IN_PROGRESS, ON_HOLD, DONE)
    }
}

/**
 * One entry in a ticket's status history
 * @param changedBy Username of whoever changed it
 */
@Serializable
data class TicketStatusChange(
    val status: TicketStatusType,
    val time: Long,
    val changedBy: String
)

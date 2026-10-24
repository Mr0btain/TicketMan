package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @param custom One of the shop's own statuses ([CustomStatus]) the ticket is in, null for the plain built-in one.
 * [status] is always that custom status's [CustomStatus.actsAs], so everything that only knows the built-in ones
 * (the queue, pickup, stats) still works
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class TicketStatus(
    val status: TicketStatusType = TicketStatusType.IN_PROGRESS,
    val time: Long = Clock.System.now().toEpochMilliseconds(),
    val custom: Uuid? = null
): SubItem {
    /** The status without when it was set, for picking and comparing */
    val choice: StatusChoice
        get() = StatusChoice(status, custom)
}

/**
 * DONE means the repair is finished and waiting for the customer, PICKED_UP closes the ticket
 */
@Serializable
enum class TicketStatusType(
    /** What staff and customers call it */
    val label: String,
    val tone: StatusTone
) {
    IN_PROGRESS("In progress", StatusTone.WARNING),
    ON_HOLD("On hold", StatusTone.DANGER),
    DONE("Ready for pickup", StatusTone.SUCCESS),
    PICKED_UP("Picked up", StatusTone.NEUTRAL);

    /** Still being worked on */
    val isOpen: Boolean
        get() = this == IN_PROGRESS || this == ON_HOLD

    companion object {
        /** Statuses that can be picked by hand, picked up is only set by finishing a pickup */
        val selectable = listOf(IN_PROGRESS, ON_HOLD, DONE)
    }
}

/**
 * A status someone can pick: a built-in one, or one of the shop's own with the built-in one it acts as
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class StatusChoice(
    val status: TicketStatusType,
    val custom: Uuid? = null
) {
    fun toStatus(time: Long = Clock.System.now().toEpochMilliseconds()): TicketStatus = TicketStatus(status, time, custom)
}

/**
 * One entry in a ticket's status history
 * @param changedBy Username of whoever changed it
 * @param custom The shop's own status it was set to, if it was one
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class TicketStatusChange(
    val status: TicketStatusType,
    val time: Long,
    val changedBy: String,
    val custom: Uuid? = null
) {
    val choice: StatusChoice
        get() = StatusChoice(status, custom)
}

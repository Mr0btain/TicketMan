package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Ticket(
    override val identifier: Uuid = Uuid.generateV7(),
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus(TicketStatusType.IN_PROGRESS, Clock.System.now().toEpochMilliseconds()),
    val priority: TicketPriority = TicketPriority(TicketPriorityType.LOW, Clock.System.now().toEpochMilliseconds()),
): MainItem(), SubItem

fun randomTicketStatus(): TicketStatus {
    val values = TicketStatusType.entries.toTypedArray()

    return TicketStatus(values[Random.nextInt(values.size)])
}

fun randomTicketPriority(): TicketPriority {
    val values = TicketPriorityType.entries.toTypedArray()

    return TicketPriority(values[Random.nextInt(values.size)])
}

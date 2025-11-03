package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID
import kotlin.random.Random

@Serializable
data class Ticket(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus(TicketStatusType.IN_PROGRESS, Time.getTime()),
    val priority: TicketPriority = TicketPriority(TicketPriorityType.LOW, Time.getTime()),
): MainItem, SubItem

fun randomTicketStatus(): TicketStatus {
    val values = TicketStatusType.entries.toTypedArray()

    return TicketStatus(values[Random.nextInt(values.size)])
}

fun randomTicketPriority(): TicketPriority {
    val values = TicketPriorityType.entries.toTypedArray()

    return TicketPriority(values[Random.nextInt(values.size)])
}

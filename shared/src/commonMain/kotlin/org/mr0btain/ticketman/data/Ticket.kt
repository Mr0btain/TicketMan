package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.TicketUpdate
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Ticket @OptIn(ExperimentalUuidApi::class) constructor(
    override val identifier: Uuid = Uuid.random(),
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus(TicketStatusType.IN_PROGRESS, Long.MAX_VALUE),
    val priority: TicketPriority = TicketPriority(TicketPriorityType.LOW, Long.MAX_VALUE),
    val time: Long = Long.MAX_VALUE
): MainDataItem

fun randomTicketStatus(): TicketStatus {
    val values = TicketStatusType.entries.toTypedArray()

    return TicketStatus(values[Random.nextInt(values.size)], getTime())
}

fun randomTicketPriority(): TicketPriority {
    val values = TicketPriorityType.entries.toTypedArray()

    return TicketPriority(values[Random.nextInt(values.size)], getTime())
}

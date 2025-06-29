package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.getTime
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID
import kotlin.random.Random

@Serializable
data class TicketInfo(
    override val identifier: String = "",
    val customerId: String = "",
    val assignedUser: String = "",
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus(TicketStatusType.IN_PROGRESS, Long.MAX_VALUE),
    val priority: TicketPriority = TicketPriority(TicketPriorityType.LOW, Long.MAX_VALUE),
    val updates: List<Note> = listOf(),
    val invoice: Invoice = Invoice(),
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

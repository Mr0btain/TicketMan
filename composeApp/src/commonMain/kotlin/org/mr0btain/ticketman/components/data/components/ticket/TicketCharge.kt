package org.mr0btain.ticketman.components.data.components.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.components.data.components.invoice.Charge
import org.mr0btain.ticketman.components.data.components.shared.Note

@Serializable
data class TicketCharge(
    val invoiceNumber: Int = 0,
    val employeeId: Int = 0,
    val note: List<Note> = listOf(),
    val charges: List<Charge> = listOf()
)
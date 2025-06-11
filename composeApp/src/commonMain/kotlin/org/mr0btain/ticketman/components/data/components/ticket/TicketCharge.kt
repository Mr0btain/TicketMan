package org.mr0btain.ticketman.components.data.components.ticket

import org.mr0btain.ticketman.components.data.components.invoice.Charge
import org.mr0btain.ticketman.components.data.components.shared.Note

data class TicketCharge(
    val invoiceNumber: Int,
    val employeeId: Int,
    val note: Note,
    val charges: List<Charge>
)
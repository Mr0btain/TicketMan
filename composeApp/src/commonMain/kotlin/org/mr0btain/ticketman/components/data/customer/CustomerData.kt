package org.mr0btain.ticketman.components.data.customer

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import kotlin.uuid.ExperimentalUuidApi

data class CustomerData @OptIn(ExperimentalUuidApi::class) constructor(
    val customer: Customer = Customer(),
    val notes: List<NoteData> = emptyList(),
    val tickets: List<TicketData> = emptyList()
)

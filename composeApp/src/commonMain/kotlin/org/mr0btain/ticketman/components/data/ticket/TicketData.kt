package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.Ticket
import kotlin.uuid.ExperimentalUuidApi

data class TicketData(
    val ticket: Ticket = Ticket(),
    val notes: List<NoteData> = emptyList()
    )
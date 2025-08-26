package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.shared.Note

@Serializable
data class TicketUpdate(
    val identifier: Int = Int.MAX_VALUE,
    val note: Note = Note(),
    val photos: List<String> = emptyList()
)
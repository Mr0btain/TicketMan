package org.mr0btain.ticketman.components.data.note

import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.shared.Note
import java.util.*

data class NoteData(
    val note: Note = Note(),
    val images: List<UUID> = emptyList()
): SubItem

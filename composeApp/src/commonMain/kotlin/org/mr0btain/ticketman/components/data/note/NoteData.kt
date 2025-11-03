package org.mr0btain.ticketman.components.data.note

import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.ExperimentalUuidApi

data class NoteData(
    val note: Note = Note(),
    val images: List<ImageData> = emptyList()
): SubItem

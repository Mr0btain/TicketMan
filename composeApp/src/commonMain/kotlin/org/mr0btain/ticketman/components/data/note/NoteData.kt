package org.mr0btain.ticketman.components.data.note

import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.ExperimentalUuidApi

data class NoteData @OptIn(ExperimentalUuidApi::class) constructor(
    val note: Note = Note(),
    val images: List<ImageBitmap> = emptyList()
)

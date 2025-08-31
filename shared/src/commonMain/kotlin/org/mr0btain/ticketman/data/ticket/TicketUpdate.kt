package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.image.ImageCollection
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.ExperimentalUuidApi

// TODO: May rename this to comment for use throughout. Might benefit from being able to add images to any place we may have notes

@Serializable
data class TicketUpdate @OptIn(ExperimentalUuidApi::class) constructor(
    val identifier: Int = Int.MAX_VALUE,
    val note: Note = Note(),
    val imageCollection: ImageCollection
)
package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.image.ImageCollection
import org.mr0btain.ticketman.data.shared.Note

// TODO: May rename this to comment for use throughout. Might benefit from being able to add images to any place we may have notes

@Serializable
data class TicketUpdate(
    val identifier: Int = Int.MAX_VALUE,
    val note: Note = Note(),
    val images: ImageCollection = ImageCollection()
)
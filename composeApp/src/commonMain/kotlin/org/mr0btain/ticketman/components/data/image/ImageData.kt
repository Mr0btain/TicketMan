package org.mr0btain.ticketman.components.data.image

import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.time.formatTime
import org.mr0btain.ticketman.data.time.getTime
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

data class ImageData(
    val identifier: UUID,
    val path: String,
    val image: ImageBitmap,
    val time: Time,
    val tags: List<String> = emptyList(),
    val description: String = ""
): SubItem

fun newImageData(parentId: UUID, image: ImageBitmap): ImageData {
    return ImageData(
        identifier = UUIDv7.randomUUID(),
        path = parentId.toString(),
        time = formatTime(getTime()),
        image = image
    )
}

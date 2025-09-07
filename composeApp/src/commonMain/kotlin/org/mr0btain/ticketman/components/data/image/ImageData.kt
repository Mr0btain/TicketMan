package org.mr0btain.ticketman.components.data.image

import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.time.formatTime
import org.mr0btain.ticketman.data.time.getTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ImageData @OptIn(ExperimentalUuidApi::class) constructor(
    val identifier: Uuid,
    val path: String,
    val image: ImageBitmap,
    val time: Time,
    val tags: List<String> = emptyList(),
    val description: String = ""
)

@OptIn(ExperimentalUuidApi::class)
fun newImageData(parentId: Uuid, image: ImageBitmap): ImageData {
    val id = Uuid.random()

    return ImageData(
        identifier = id,
        path = parentId.toString(),
        time = formatTime(getTime()),
        image = image
    )
}

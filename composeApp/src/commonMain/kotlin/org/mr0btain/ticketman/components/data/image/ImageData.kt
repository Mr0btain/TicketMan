package org.mr0btain.ticketman.components.data.image

import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.data.time.Time

data class ImageData(
    val image: ImageBitmap,
    val time: Time,
    val tags: List<String> = emptyList(),
    val description: String = ""
)

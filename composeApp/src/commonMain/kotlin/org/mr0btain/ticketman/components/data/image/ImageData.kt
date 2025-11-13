package org.mr0btain.ticketman.components.data.image

import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

// TODO: Maybe redo this so it only holds like meta data about the image.
//  Have the actual image and thumbnail separate and get them as needed
data class ImageData(
    override val identifier: UUID = UUIDv7.randomUUID(),
    val path: String,
    val image: ImageBitmap,
    val tags: List<String> = emptyList(),
    val description: String = ""
): MainItem, SubItem

package org.mr0btain.ticketman.data.image

import kotlinx.serialization.Serializable

/**
 * Holds a collection of images, with the images being stored as a list of base64 encoded strings that need to be decoded to be used
 * @param selectedImage The index of the selected image, or last viewed image.
 * @param images List of base64 encoded strings of images that can be decoded to an ImageBitmap
 */

@Serializable
data class ImageCollection(
    val selectedImage: Int = 0,
    val images: List<ByteArray> = emptyList()
)

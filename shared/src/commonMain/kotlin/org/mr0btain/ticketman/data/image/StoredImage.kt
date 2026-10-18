package org.mr0btain.ticketman.data.image

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A photo the server holds. The bytes live on the server's disk, keyed by [identifier], and never change once
 * uploaded, so they can be cached forever. Replacing a photo means uploading a new one
 * @param width Width in pixels of the [ImageVariant.ORIGINAL], 0 if unknown
 * @param height Height in pixels of the [ImageVariant.ORIGINAL], 0 if unknown
 * @param sizeBytes Size of the [ImageVariant.ORIGINAL] on the server
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class StoredImage(
    override val identifier: Uuid = Uuid.generateV7(),
    val description: String = "",
    val tags: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0
): MainItem(), SubItem {
    /** Width over height, or null when the size isn't known */
    val aspectRatio: Float?
        get() = if (width > 0 && height > 0) width.toFloat() / height else null
}

package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.getTime
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.image_directory
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Saves an image to disk
 * @param identifier The identifier of the item this belongs to. This will be the folder its saved in. Just for some little organization
 * @param imageBitmap The image to be saved
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun saveImageToDatabase(
    identifier: Uuid,
    imageBitmap: ImageBitmap
): StoredImage {
    // Generate an id
    val id = Uuid.random().toString()
    val file = FileKit.databasesDir / Res.string.image_directory.toString() / identifier.toString() / id

    // Write data to disk
    file.write(imageBitmap.toByteArray())

    return StoredImage(file.path, getTime())
}


expect fun decodeBase64ToImageBitmap(base64: String): ImageBitmap?

expect fun ImageBitmap.encodeToBase64(): String

expect fun ByteArray.toImageBitmap(): ImageBitmap
expect fun ImageBitmap.toByteArray(): ByteArray
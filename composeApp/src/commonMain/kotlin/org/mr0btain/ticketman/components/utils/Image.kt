package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.*
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import java.util.UUID

expect fun ByteArray.toImageBitmap(): ImageBitmap

expect fun ImageBitmap.toByteArray(): ByteArray

object ImageUtils {
    val TAG: String = "ImageHandler"

    suspend fun getImage(image: StoredImage): ImageBitmap? {
        return try {
            val file = PlatformFile("${image.directory}/${image.identifier}")

            if (file.exists()) {
                file.readBytes().toImageBitmap()
            } else {
                AppLogger.e("$TAG:GetImage", "File does not exist!")
                null
            }
        } catch (ex: Exception) {
            AppLogger.e("$TAG:GetImage", SimpleResult.FAIL, ex)
            null
        }
    }

    suspend fun getThumbnail(image: StoredImage): ImageBitmap? {
        return try {
            val file = PlatformFile("${image.directory}/${image.identifier}.thumb")

            if (file.exists()) {
                file.readBytes().toImageBitmap()
            } else {
                AppLogger.e("$TAG:GetThumbnail", "File does not exist!")
                null
            }
        } catch (ex: Exception) {
            AppLogger.e("$TAG:GetThumbnail", SimpleResult.FAIL, ex)
            null
        }
    }

    // TODO: Have this generate a thumbnail as well
    /**
     * This will save an image and a generated thumbnail to storage in the database/images/directory folder.
     * This DOES NOT add the relevant data to the database. Simply stores the image itself
     */
    suspend fun saveImage(
        identifier: UUID,
        directory: String,
        image: ImageBitmap
    ): Boolean {
        return try {
            // Set parent directory
            val parentDirectory = FileKit.databasesDir / "images" / directory

            // Create directories
            parentDirectory.createDirectories()

            // Set and create actual file
            val file = PlatformFile(parentDirectory, "$identifier")

            // Check if file exists already, if so remove it.
            // TODO: Not sure if we can just erase the bytes or replace them instead of doing this?
            if (file.exists()) file.delete()

            // Create new file
            file.file.createNewFile()

            // Write data to disk
            file.write(image.toByteArray())

            // Return success
            true
        } catch (ex: Exception) {
            // Else we've failed somehow, log and return failure
            AppLogger.e("$TAG:SaveImage", SimpleResult.FAIL, ex)
            false
        }
    }

    suspend fun saveImages(
        directory: String,
        images: List<Pair<UUID, ImageBitmap>>
    ): Boolean {
        var result = true

        images.forEach { (id, bitmap) ->
            if (!saveImage(id, directory, bitmap))
                result = false
        }

        return result
    }
}



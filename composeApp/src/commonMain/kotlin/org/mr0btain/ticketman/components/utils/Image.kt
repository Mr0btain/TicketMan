package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.*
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.time.formatTime
import org.mr0btain.ticketman.data.time.getTime
import org.mr0btain.ticketman.data.time.tolong
import org.mr0btain.ticketman.data.uuid.UUIDv7
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.image_directory
import java.util.UUID

/**
 * Saves an image to disk
 * @param identifier The identifier of the item this belongs to. This will be the folder its saved in. Just for some little organization
 * @param imageBitmap The image to be saved
 */
suspend fun saveImageToDatabaseFolder(
    identifier: UUID,
    imageBitmap: ImageBitmap
): StoredImage? {
    return try {
        // Generate an id
        val id = UUIDv7.randomUUID()
        val file = FileKit.databasesDir / identifier.toString() / id.toString()

        // Write data to disk
        file.write(imageBitmap.toByteArray())

        StoredImage(id, file.path, getTime())
    } catch (ex: Exception) {
        AppLogger.i("SaveImage", SimpleResult.FAIL)
        ex.printStackTrace()
        null
    }
}

/**
 * Saves an image to disk. We use databasedir from FileKit and save it in the folder path, identifier as the file name
 * @param imageData Image data to write to database folder
 */
suspend fun saveImageToDatabaseFolder(
    imageData: ImageData
): StoredImage? {
    return try {
        // Set parent directory
        val parentDirectory = FileKit.databasesDir / "images" / imageData.path

        // Create directories
        parentDirectory.createDirectories()

        // Set and create actual file
        val file = PlatformFile(parentDirectory, "${imageData.identifier}")
        file.file.createNewFile()

        // Write data to disk
        file.write(imageData.image.toByteArray())

        // Return result
        StoredImage(imageData.identifier, file.path, getTime())
    } catch (ex: Exception) {
        AppLogger.i("SaveImage", SimpleResult.FAIL)
        ex.printStackTrace()
        null
    }
}

suspend fun flushImageIfNotInDatabase(imageData: ImageData): StoredImage? {
    return try {
        val file = FileKit.databasesDir / Res.string.image_directory.toString() / imageData.path

        if (file.exists()) {
            StoredImage(
                identifier = imageData.identifier,
                path = imageData.path,
                time = imageData.time.tolong()
            )
        } else {
            saveImageToDatabaseFolder(imageData)
        }
    } catch (ex: Exception) {
        null
    }
}

suspend fun flushImagesToDisk(imageList: List<ImageData>): List<StoredImage> {
    val result = mutableListOf<StoredImage>()

    imageList.forEach { imageData ->
        flushImageIfNotInDatabase(imageData)?.let { storedImage ->
            result.add(storedImage)
        }
    }

    return result
}


expect fun decodeBase64ToImageBitmap(base64: String): ImageBitmap?

expect fun ImageBitmap.encodeToBase64(): String

expect fun ByteArray.toImageBitmap(): ImageBitmap
expect fun ImageBitmap.toByteArray(): ByteArray

suspend fun pullImagesFromDisk(storedImages: List<StoredImage>): List<ImageData> {
    val result = mutableListOf<ImageData>()

    storedImages.forEach { storedImage ->
        try {
            val file = PlatformFile(storedImage.path)

            if (file.exists()) {
                result.add(
                    ImageData(
                        identifier = storedImage.identifier,
                        image = file.readBytes().toImageBitmap(),
                        time = formatTime(storedImage.time),
                        path = file.path
                    )
                )
            }
        } catch (ex: Exception) {
            AppLogger.i("GetImages", "Failed to get image: ${storedImage.identifier}")
            ex.printStackTrace()
        }
    }

    return result
}



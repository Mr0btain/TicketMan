package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.*
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.uuid.UUIDv7
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.image_directory
import java.util.UUID

// TODO: Setup thumbnails and such. Really need to get a solid foundation for image stuff. Maybe a Class or Object to handle operations?

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
        StoredImage(imageData.identifier, file.path)
    } catch (ex: Exception) {
        AppLogger.e("SaveImage", SimpleResult.FAIL, ex)
        null
    }
}

suspend fun storeImageIfNotInDatabase(imageData: ImageData): StoredImage? {
    return try {
        val file = FileKit.databasesDir / Res.string.image_directory.toString() / imageData.path

        if (file.exists()) {
            StoredImage(
                identifier = imageData.identifier,
                path = imageData.path
            )
        } else {
            saveImageToDatabaseFolder(imageData)
        }
    } catch (ex: Exception) {
        AppLogger.e("FlushImageIfNotInDatabase", SimpleResult.FAIL, ex)
        null
    }
}

suspend fun writeImagesToDisk(imageList: List<ImageData>): List<StoredImage> {
    val result = mutableListOf<StoredImage>()

    imageList.forEach { imageData ->
        storeImageIfNotInDatabase(imageData)?.let { storedImage ->
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
                        path = file.path
                    )
                )
            }
        } catch (ex: Exception) {
            AppLogger.e("GetImages", "Failed to get image: ${storedImage.identifier}", ex)
        }
    }

    return result
}



package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.compose.util.encodeToByteArray
import io.github.vinceglb.filekit.dialogs.compose.util.toImageBitmap
import io.ktor.client.request.invoke
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.Uuid

object ImageUtils {
    val TAG: String = "ImageHandler"
    //val databaseDirectory = FileKit.databasesDir / "images"
    // TODO:Fix
    val databaseDirectory = ""

    const val MAX_THUMB_SIZE = 200

    // Were going to use String instead of UUID because we can get file names as string
    // and not have to convert them to UUID for every image.
    val thumbnailCache = LruCache<String, ImageBitmap>(20, "ThumbnailCache")
    val imageCache = LruCache<String, ImageBitmap>(10, "ImageCache")

    suspend fun getImagesForId(identifier: Uuid): List<ImageBitmap> =
        getImagesFromFolder(identifier.toString(), false)

    suspend fun getThumbnailsForId(identifier: Uuid): List<ImageBitmap> =
        getImagesFromFolder("$identifier", true)

    private suspend fun getImagesFromFolder(folder: String, thumbnail: Boolean): List<ImageBitmap> {
        //val dir = databaseDirectory / folder
        val images = mutableListOf<ImageBitmap>()

        /*
        if (dir.isDirectory()) {
            dir.list().forEach { file ->
                try {
                    if (thumbnail && file.name.startsWith("thumb_")) {
                        thumbnailCache.get(file.nameWithoutExtension)?.let { imageBitmap ->
                            images.add(imageBitmap)
                        } ?: images.add(file.toImageBitmap())
                    } else if (!file.name.startsWith("thumb_")) {
                        imageCache.get(file.nameWithoutExtension)?.let { imageBitmap ->
                            images.add(imageBitmap)
                        } ?: images.add(file.toImageBitmap())
                    }
                } catch (ex: Exception) {
                    AppLogger.i(TAG, "Error reading image file: ${file.name}")
                    ex.printStackTrace()
                }
            }
        }
         */

        return images
    }

    // TODO: (Fix image stuff)
    suspend fun getImage(image: StoredImage, thumbnail: Boolean): ImageBitmap? {
        return try {
            /*
            // Check if we have this image cached and return it if so
            val cached = imageCache.get(image.identifier.toString())
            if (cached != null) return cached

            val fileName = if (thumbnail) "thumb_${image.identifier}.png" else "${image.identifier}.png"

            // Else we need to find and pull the image
            val file = PlatformFile("${image.directory}/$fileName")

            // Make sure it exists
            if (file.exists()) {
                file.toImageBitmap().also { bitmap ->
                    if (thumbnail) {
                        thumbnailCache.put(file.nameWithoutExtension, bitmap)
                    } else {
                        imageCache.put(file.nameWithoutExtension, bitmap)
                    }
                }
            } else {
                AppLogger.e("$TAG:GetImage", "File does not exist!")
                null
            }
             */
            null
        } catch (ex: Exception) {
            AppLogger.e("$TAG:GetImage", SimpleResult.FAIL, ex)
            null
        }
    }

    suspend fun saveImageFromFile(
        parentId: Uuid,
        identifier: Uuid,
        file: PlatformFile
    ): Boolean {
        return try {
            // Make new file
            /*
            val newFile = databaseDirectory / parentId.toString() / "$identifier.png"

            // See if directory exists
            if (!(databaseDirectory / parentId.toString()).exists())
                newFile.createDirectories()

            // Make sure new file is compressed to manageable size
            val compressed = file.readBytes()

            // Write file to db
            newFile write compressed

            // Make a write thumbnail
            val thumbnail = databaseDirectory / parentId.toString() / "thumb_$identifier.png"
            thumbnail write createThumbnail(compressed)
             */

            true
        } catch (ex: Exception) {
            AppLogger.i(TAG, "Failed to save image from file")
            ex.printStackTrace()
            false
        }
    }

    /**
     * This will save an image and a generated thumbnail to storage in the database/images/directory folder.
     * This DOES NOT add the relevant data to the database. Simply stores the image itself
     */
    suspend fun saveImage(
        identifier: Uuid,
        directory: String,
        image: ImageBitmap
    ): Boolean {
        return try {
            val parentDirectory = ""//FileKit.databasesDir / "images" / directory

           // if (!parentDirectory.exists()) parentDirectory.createDirectories()

            // Set and create actual file
            //val file = parentDirectory / "$identifier.png"
            //val thumb = parentDirectory / "thumb_$identifier.png"

            // Make directory if it doesn't exist
            //file.createDirectories()

            // Create new file
            //file.file.createNewFile()
            //thumb.file.createNewFile()

            val imageBytes = image.encodeToByteArray()

            // Write data to disk
            //file write imageBytes
            //thumb write createThumbnail(imageBytes)

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
        images: List<Pair<Uuid, ImageBitmap>>
    ): Boolean {
        var result = true

        images.forEach { (id, bitmap) ->
            if (!saveImage(id, directory, bitmap))
                result = false
        }

        return result
    }

    private suspend fun createThumbnail(image: ByteArray): ByteArray {
        // Create a thumbnail by compressing and resizing
        return byteArrayOf()
        /*
        return FileKit.compressImage(
            bytes = image,
            quality = 85,
            maxWidth = MAX_THUMB_SIZE,
            maxHeight = MAX_THUMB_SIZE,
            imageFormat = ImageFormat.PNG
        )
         */
    }
}



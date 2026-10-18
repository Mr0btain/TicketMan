package org.mr0btain.ticketman.components.image

import net.coobird.thumbnailator.Thumbnails
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.error.ImageError
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * Turns an uploaded file into the JPEG variants in [ImageVariant]. Everything is decoded and re-encoded, which
 * proves the file really is an image, turns it upright from its EXIF orientation and drops the rest of its
 * metadata (like where the photo was taken)
 */
object ImageProcessor {
    private const val TAG = "ImageProcessor"
    private const val PROCESS_TAG = "$TAG:Process"

    /**
     * @param width Width of the original variant after rotating
     * @param sha256 Hash of the original variant, used as its ETag
     */
    data class Processed(
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val sha256: String
    )

    /**
     * Writes every variant of [source] into [outputFolder] as `<variant>.jpg`
     */
    fun process(source: File, outputFolder: File): Result<Processed, ImageError> {
        // Header only, so a small file claiming to be enormous is caught before it's decoded
        val (sourceWidth, sourceHeight) = readSize(source) ?: return Result.Error(ImageError.UNSUPPORTED)

        if (sourceWidth <= 0 || sourceHeight <= 0) return Result.Error(ImageError.UNSUPPORTED)
        if (sourceWidth.toLong() * sourceHeight > ImageLimits.MAX_PIXELS) return Result.Error(ImageError.TOO_LARGE)

        return try {
            outputFolder.mkdirs()

            val original = Thumbnails.of(source)
                .scale(scaleToFit(max(sourceWidth, sourceHeight), ImageVariant.ORIGINAL.maxEdge))
                .useExifOrientation(true)
                .asBufferedImage()
                .flattened()

            ImageVariant.entries.forEach { variant ->
                Thumbnails.of(original)
                    .scale(scaleToFit(max(original.width, original.height), variant.maxEdge))
                    .outputFormat("jpg")
                    .outputQuality(variant.quality)
                    .toFile(File(outputFolder, "${variant.path}.jpg"))
            }

            val originalFile = File(outputFolder, "${ImageVariant.ORIGINAL.path}.jpg")

            Result.Success(
                Processed(
                    width = original.width,
                    height = original.height,
                    sizeBytes = originalFile.length(),
                    sha256 = sha256(originalFile)
                )
            )
        } catch (ex: Exception) {
            AppLogger.e(PROCESS_TAG, SimpleResult.FAIL, ex)
            Result.Error(ImageError.UNSUPPORTED)
        } catch (ex: OutOfMemoryError) {
            AppLogger.e(PROCESS_TAG, "Ran out of memory on a ${sourceWidth}x$sourceHeight image", ex)
            Result.Error(ImageError.TOO_LARGE)
        }
    }

    /** Width and height from the file's header, null if no reader knows the format */
    fun readSize(file: File): Pair<Int, Int>? {
        return try {
            ImageIO.createImageInputStream(file)?.use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) return null

                val reader = readers.next()
                try {
                    reader.input = input
                    Pair(reader.getWidth(0), reader.getHeight(0))
                } finally {
                    reader.dispose()
                }
            }
        } catch (ex: Exception) {
            AppLogger.i(TAG, "Couldn't read image size: ${ex.message}")
            null
        }
    }

    /** Scale that fits [longestEdge] inside [maxEdge], never enlarging */
    private fun scaleToFit(longestEdge: Int, maxEdge: Int): Double =
        min(1.0, maxEdge.toDouble() / longestEdge)

    /** JPEG has no transparency, so see through areas go white instead of black */
    private fun BufferedImage.flattened(): BufferedImage {
        if (type == BufferedImage.TYPE_INT_RGB) return this

        val rgb = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, width, height)
            drawImage(this@flattened, 0, 0, null)
            dispose()
        }

        return rgb
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

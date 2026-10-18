package org.mr0btain.ticketman.components.image

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.logging.AppLogger

private const val TAG = "ShrinkForUpload"

// Phone photos are usually 3 to 12 MB, smaller ones aren't worth recompressing
private const val SHRINK_ABOVE_BYTES = 1_500_000

// FileKit's Android compressor turns the photo upright from its EXIF orientation first, so this is safe here
actual suspend fun shrinkForUpload(bytes: ByteArray): ByteArray {
    if (bytes.size <= SHRINK_ABOVE_BYTES) return bytes

    return try {
        FileKit.compressImage(
            bytes = bytes,
            imageFormat = ImageFormat.JPEG,
            quality = 85,
            maxWidth = ImageLimits.MAX_EDGE,
            maxHeight = ImageLimits.MAX_EDGE
        )
    } catch (ex: Exception) {
        // Let the server have a go instead
        AppLogger.e(TAG, "Couldn't shrink photo, sending it as is", ex)
        bytes
    }
}

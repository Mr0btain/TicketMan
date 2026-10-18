package org.mr0btain.ticketman.components.image

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.image.ImageRemoteRepository
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.NetworkError

/**
 * What screens use for photos: sending picked files to the server and finding where a photo is fetched from
 */
class ImageClient(
    private val repository: ImageRemoteRepository,
    private val httpClient: HttpClient
) {
    private val TAG = "ImageClient"
    private val UPLOAD_TAG = "$TAG:Upload"

    /**
     * Coil loader that fetches through the app's http client. Photos never change once uploaded, so whatever is
     * in the memory or disk cache is used without asking the server again
     */
    fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
            .diskCache { imageDiskCache(context, DISK_CACHE_BYTES) }
            .crossfade(true)
            .build()

    /** Url of a photo's [variant], null with no server set */
    fun urlFor(image: StoredImage, variant: ImageVariant): String? = repository.urlFor(image.identifier, variant)

    /**
     * Reads, shrinks where the platform can, and uploads a picked file
     * @param onProgress 0 to 1 as the upload goes
     */
    suspend fun upload(
        file: PlatformFile,
        description: String = "",
        onProgress: (Float) -> Unit = {}
    ): Result<StoredImage, ServerError> {
        val bytes = try {
            shrinkForUpload(file.readBytes())
        } catch (ex: Exception) {
            AppLogger.e(UPLOAD_TAG, "Couldn't read ${file.name}", ex)
            return Result.Error(ServerError(NetworkError.UNKNOWN, "Couldn't read that file"))
        }

        if (bytes.size > ImageLimits.MAX_UPLOAD_BYTES) {
            return Result.Error(ServerError(NetworkError.PAYLOAD_TOO_LARGE, "That photo is too big"))
        }

        return repository.upload(bytes, file.name, description) { sent, total ->
            if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
        }
    }
}

private const val MEMORY_CACHE_PERCENT = 0.15
private const val DISK_CACHE_BYTES = 250L * 1024 * 1024

/** Disk cache for downloaded photos, null where there's no file system to keep one in (the browser has its own) */
expect fun imageDiskCache(context: PlatformContext, maxSizeBytes: Long): DiskCache?

/**
 * Makes a photo smaller before it's sent, where that can be done without losing its orientation. Android scales
 * to [ImageLimits.MAX_EDGE] as a JPEG, other platforms send it as is and leave it to the server
 */
expect suspend fun shrinkForUpload(bytes: ByteArray): ByteArray

/** Photo client for the screen, null in previews and render tests where there's no server */
val LocalImageClient = staticCompositionLocalOf<ImageClient?> { null }

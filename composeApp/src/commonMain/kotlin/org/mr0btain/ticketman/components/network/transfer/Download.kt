package org.mr0btain.ticketman.components.network.transfer

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.RawSink
import kotlinx.io.buffered
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.database.transferTimeout
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.NetworkError

/** How a download to a file ended */
sealed interface DownloadOutcome {
    data class Saved(val bytes: Long) : DownloadOutcome

    /** Nowhere to save was picked */
    data object Cancelled : DownloadOutcome

    /** The browser is downloading it, it shows its own progress */
    data object HandedToBrowser : DownloadOutcome

    data class Failed(val error: ServerError) : DownloadOutcome
}

private const val TAG = "Download"
private const val CHUNK_BYTES = 64 * 1024

/**
 * Streams [url] into [openSink] a chunk at a time, so nothing near the whole file is ever in memory
 * @param expectedBytes Size the file should be, a short download fails instead of leaving a cut off file
 * @param onProgress Bytes so far and the total, if the server said
 */
suspend fun HttpClient.downloadTo(
    url: String,
    expectedBytes: Long?,
    openSink: () -> RawSink,
    onProgress: (received: Long, total: Long?) -> Unit = { _, _ -> }
): Result<Long, ServerError> {
    val result = networkErrorTryCatch(TAG) {
        prepareGet(url) { transferTimeout() }.execute { response ->
            if (!response.status.isSuccess()) {
                val failed = handleActionHttpResponse(TAG, response) as? Result.Error
                return@execute Result.Error(failed?.error ?: ServerError(NetworkError.UNKNOWN))
            }

            val total = response.contentLength() ?: expectedBytes
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(CHUNK_BYTES)
            var received = 0L

            openSink().buffered().use { sink ->
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break

                    sink.write(buffer, 0, read)
                    received += read
                    onProgress(received, total)
                }
            }

            if (expectedBytes != null && received != expectedBytes) {
                Result.Error(ServerError(NetworkError.UNKNOWN, "Download stopped early ($received of $expectedBytes bytes)"))
            } else {
                Result.Success(received)
            }
        }
    }

    return result ?: Result.Error(ServerError(NetworkError.NO_INTERNET, "The download was cut off"))
}

/**
 * Asks where to save, then downloads [url] there. In the browser the download is handed to the browser instead,
 * which saves straight to disk
 */
expect suspend fun saveDownload(
    client: HttpClient,
    url: String,
    fileName: String,
    expectedBytes: Long?,
    onProgress: (received: Long, total: Long?) -> Unit
): DownloadOutcome

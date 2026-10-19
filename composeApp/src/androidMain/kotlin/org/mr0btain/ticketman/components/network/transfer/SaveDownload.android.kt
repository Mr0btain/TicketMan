package org.mr0btain.ticketman.components.network.transfer

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.sink
import io.ktor.client.*
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.logging.AppLogger

private const val TAG = "SaveDownload"

actual suspend fun saveDownload(
    client: HttpClient,
    url: String,
    fileName: String,
    expectedBytes: Long?,
    onProgress: (received: Long, total: Long?) -> Unit
): DownloadOutcome {
    val file = FileKit.openFileSaver(
        suggestedName = fileName.substringBeforeLast('.'),
        defaultExtension = fileName.substringAfterLast('.', "")
    ) ?: return DownloadOutcome.Cancelled

    return when (val result = client.downloadTo(url, expectedBytes, { file.sink() }, onProgress)) {
        is TmResult.Success -> DownloadOutcome.Saved(result.data)

        is TmResult.Error -> {
            // Don't leave half a backup lying around looking like a whole one
            try {
                file.delete(mustExist = false)
            } catch (ex: Exception) {
                AppLogger.e(TAG, "Couldn't remove the partial download", ex)
            }

            DownloadOutcome.Failed(result.error)
        }
    }
}

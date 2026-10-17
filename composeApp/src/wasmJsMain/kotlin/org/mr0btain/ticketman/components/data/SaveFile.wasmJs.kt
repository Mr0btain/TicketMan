package org.mr0btain.ticketman.components.data

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult

private const val TAG = "SaveFile"

actual suspend fun saveFile(bytes: ByteArray, suggestedName: String, extension: String): Boolean {
    return try {
        FileKit.download(bytes = bytes, fileName = "$suggestedName.$extension")
        AppLogger.m(TAG, SimpleResult.SUCCESS)
        true
    } catch (ex: Exception) {
        AppLogger.e(TAG, SimpleResult.FAIL, ex)
        false
    }
}

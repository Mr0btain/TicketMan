package org.mr0btain.ticketman.components.data

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult

private const val TAG = "SaveFile"

actual suspend fun saveFile(bytes: ByteArray, suggestedName: String, extension: String): Boolean {
    return try {
        val file = FileKit.openFileSaver(suggestedName = suggestedName, defaultExtension = extension)
            ?: return false

        file.write(bytes)
        AppLogger.m(TAG, SimpleResult.SUCCESS)
        true
    } catch (ex: Exception) {
        AppLogger.e(TAG, SimpleResult.FAIL, ex)
        false
    }
}

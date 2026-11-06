package org.mr0btain.ticketman.components.logging

import kotlinx.io.files.Path
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.LogBook
import org.mr0btain.ticketman.logging.LogFileStore
import org.mr0btain.ticketman.util.AppDir

/**
 * This device's log files, in `logs/` under the app's folder, kept as Settings says ([LocalStorage.logSettings]).
 * The web has no folder, so a browser only keeps what it logged since the page opened, in memory
 */
object ClientLogs {
    private const val TAG = "ClientLogs"
    private const val FOLDER = "logs"

    /** Whether this device can save log files at all */
    var savesFiles: Boolean = false
        private set

    /** Starts saving, with what was logged since the app started first. Called once from initKoin */
    fun start(appDir: AppDir, localStorage: LocalStorage) {
        val folder = appDir.path?.let { Path(it, FOLDER) } ?: return
        try {
            val store = LogFileStore(folder, localStorage.logSettings)
            if (!store.usable) return
            LogBook.attach(store)
            savesFiles = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Couldn't start saving logs in $folder", e)
        }
    }
}

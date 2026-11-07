package org.mr0btain.ticketman.components.service

import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.mr0btain.applogger.AppLogger
import org.mr0btain.applogger.LogBook
import org.mr0btain.applogger.LogFileStore
import org.mr0btain.applogger.LogOverview
import org.mr0btain.applogger.LogSettings
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The server's log files, in `<dataPath>/server/logs/`, with the settings an admin picked kept beside them in
 * `settings.json`. Not in the database, so the log is saved from the first line of start up, before the database
 * opens, and a restore doesn't change how logs are kept.
 *
 * Every line goes through [LogBook]: [AppLogger]'s, and the libraries' through `LogBookAppender`
 * @param folder Where the files go. Null keeps lines in memory only
 */
class ServerLogs(private val folder: File?) {
    private val TAG = "ServerLogs"
    private val settingsFile = folder?.let { File(it, SETTINGS_FILE) }

    /** Starts saving lines to files, the ones logged since the process started first */
    fun start() {
        val folder = folder ?: return
        val settings = readSettings()
        LogBook.attach(LogFileStore(Path(folder.path), "ticketman", settings))
        AppLogger.i(TAG, "Saving logs in ${folder.path}, keeping ${settings.keepFiles} files up to ${settings.maxTotalMb} MB, a new one each day at ${settings.rolloverText}")
    }

    /** Stops writing files, so they can be moved or deleted. Called when the server stops */
    fun stop() = LogBook.detach()

    fun overview(): LogOverview = LogOverview(
        settings = LogBook.settings ?: readSettings(),
        files = LogBook.files(),
        storing = LogBook.storing
    )

    /** Saves and applies [settings], clamped to their ranges. Null when they couldn't be saved */
    fun update(settings: LogSettings): LogSettings? {
        val value = settings.normalized()
        val file = settingsFile ?: return null
        try {
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, "$SETTINGS_FILE.tmp")
            temp.writeText(json.encodeToString(LogSettings.serializer(), value))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Couldn't save the log settings", e)
            return null
        }
        LogBook.updateSettings(value)
        return value
    }

    /**
     * Writes the lines logged between [from] and [to] (epoch milliseconds, null for no limit) to [out]
     * @return How many lines were written
     */
    fun export(from: Long?, to: Long?, out: OutputStream): Int {
        val sink = out.asSink().buffered()
        val count = LogBook.export(from, to, sink)
        sink.flush()
        return count
    }

    private fun readSettings(): LogSettings {
        val file = settingsFile?.takeIf { it.isFile } ?: return LogSettings()
        return try {
            json.decodeFromString(LogSettings.serializer(), file.readText()).normalized()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Couldn't read ${file.path}, using the default log settings", e)
            LogSettings()
        }
    }

    companion object {
        const val SETTINGS_FILE = "settings.json"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Where the logs go for a server keeping its data in [dataPath] */
        fun folderFor(dataPath: String): File = File(dataPath, "server/logs")
    }
}

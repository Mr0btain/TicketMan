package org.mr0btain.ticketman.logging

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine
import kotlinx.io.writeString

/**
 * Log files in [directory], one per day starting at [LogSettings.rolloverMinute], named for when they started
 * ([LogFormat.fileName]). Keeps [LogSettings.keepFiles] of them and no more than [LogSettings.maxTotalMb] together.
 *
 * Not safe to use from several threads on its own: [LogBook] holds it and calls it under its lock. Never logs
 * through [AppLogger], which would call back in; problems go to the console with [println]
 * @param zone The time zone files and lines are in, the machine's own
 */
class LogFileStore(
    val directory: Path,
    settings: LogSettings = LogSettings(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val fileSystem: FileSystem = SystemFileSystem
) {
    var settings: LogSettings = settings.normalized()
        private set

    private var sink: Sink? = null
    private var currentName: String? = null
    private var currentSize = 0L

    // The other files' size together as last pruned, so the oldest go once the current one grows past what's left
    private var othersSize = 0L
    private var nextStart = 0L
    private var full = false
    private var broken = false

    /** False when the folder couldn't be made, nothing is saved */
    val usable: Boolean get() = !broken

    init {
        try {
            fileSystem.createDirectories(directory)
        } catch (e: Exception) {
            broken = true
            println("ERROR: [$TAG] Can't make the log folder $directory, logs won't be saved: ${e.message}")
        }
    }

    /** Appends [line], starting a new file first when its time has come */
    fun write(line: LogLine) {
        if (broken) return
        try {
            if (sink == null || line.time >= nextStart) open(line.time)
            if (full) return

            val text = LogFormat.format(line, zone()) + "\n"
            val size = text.encodeToByteArray().size

            if (currentSize + size > settings.maxTotalBytes) {
                full = true
                val notice = LogFormat.format(
                    LogLine(line.sequence, line.time, LogLevel.WARN, TAG, "This log file reached the ${settings.maxTotalMb} MB limit, nothing more is saved until the next one starts at ${settings.rolloverText}"),
                    zone()
                ) + "\n"
                sink?.writeString(notice)
                sink?.flush()
                return
            }

            sink?.writeString(text)
            sink?.flush()
            currentSize += size
            if (othersSize > 0 && currentSize + othersSize > settings.maxTotalBytes) prune()
        } catch (e: Exception) {
            // A full disk or a folder taken away. Try again with a fresh file on the next line
            println("ERROR: [$TAG] Couldn't write the log file: ${e.message}")
            close()
        }
    }

    /** Takes new settings: the file changes on the next line if the rollover time moved, and old ones are trimmed now */
    fun update(settings: LogSettings) {
        this.settings = settings.normalized()
        nextStart = 0
        full = false
        prune()
    }

    /** Every log file in the folder, oldest first */
    fun files(): List<LogFileInfo> = logFiles().map { (path, start) ->
        LogFileInfo(
            name = path.name,
            startedAt = start.toInstant(zone()).toEpochMilliseconds(),
            sizeBytes = fileSystem.metadataOrNull(path)?.size ?: 0,
            current = path.name == currentName
        )
    }

    /**
     * Writes the lines logged between [from] and [to] (epoch milliseconds, both included, null for no limit) to [out],
     * with a stack trace staying with the line it belongs to. Safe while lines are being written, the last one may
     * be left out
     * @return How many lines were written
     */
    fun export(files: List<LogFileInfo>, from: Long?, to: Long?, out: Sink): Int {
        var written = 0
        files.forEachIndexed { index, file ->
            val end = files.getOrNull(index + 1)?.startedAt ?: Long.MAX_VALUE
            if (from != null && end <= from) return@forEachIndexed
            if (to != null && file.startedAt > to) return@forEachIndexed

            val path = Path(directory, file.name)
            if (!fileSystem.exists(path)) return@forEachIndexed
            try {
                fileSystem.source(path).buffered().use { source ->
                    var keep = false
                    while (true) {
                        val text = source.readLine() ?: break
                        val time = LogFormat.parseTime(text, zone())
                        if (time != null) keep = (from == null || time >= from) && (to == null || time <= to)
                        if (keep) {
                            out.writeString(text)
                            out.writeString("\n")
                            if (time != null) written++
                        }
                    }
                }
            } catch (e: Exception) {
                println("ERROR: [$TAG] Couldn't read ${file.name}: ${e.message}")
            }
        }
        return written
    }

    fun flush() {
        try {
            sink?.flush()
        } catch (_: Exception) {
        }
    }

    fun close() {
        try {
            sink?.close()
        } catch (_: Exception) {
        }
        sink = null
        currentName = null
    }

    private fun open(time: Long) {
        val start = LogFormat.periodStart(time, settings.rolloverMinute, zone())
        val name = LogFormat.fileName(start)
        nextStart = LogFormat.periodEnd(start, zone())
        if (name == currentName && sink != null) return

        close()
        val path = Path(directory, name)
        sink = fileSystem.sink(path, append = true).buffered()
        currentName = name
        currentSize = fileSystem.metadataOrNull(path)?.size ?: 0
        full = currentSize >= settings.maxTotalBytes
        prune()
    }

    /** Deletes the oldest files past [LogSettings.keepFiles], then past [LogSettings.maxTotalMb]. Never the one being written */
    private fun prune() {
        try {
            val newestFirst = logFiles().reversed().map { it.first }
            val kept = mutableListOf<Path>()
            var total = 0L
            var others = 0L

            newestFirst.forEach { path ->
                val size = fileSystem.metadataOrNull(path)?.size ?: 0
                val current = path.name == currentName
                if (current || (kept.size < settings.keepFiles && total + size <= settings.maxTotalBytes)) {
                    kept += path
                    total += size
                    if (!current) others += size
                } else {
                    fileSystem.delete(path, mustExist = false)
                }
            }
            othersSize = others
        } catch (e: Exception) {
            println("ERROR: [$TAG] Couldn't clear out old log files: ${e.message}")
        }
    }

    // The folder's log files with when each started, oldest first. Names sort by time
    private fun logFiles() = runCatching { fileSystem.list(directory) }.getOrDefault(emptyList())
        .mapNotNull { path -> LogFormat.fileStart(path.name)?.let { path to it } }
        .sortedBy { it.first.name }

    companion object {
        private const val TAG = "LogFileStore"
    }
}

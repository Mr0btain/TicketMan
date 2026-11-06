package org.mr0btain.ticketman.logging

import kotlinx.serialization.Serializable

/** How serious a log line is. [WARN] only comes from the server's own libraries (Ktor, Netty, Exposed) */
@Serializable
enum class LogLevel(val label: String) {
    ERROR("ERROR"),
    WARN("WARN"),
    INFO("INFO"),
    DEBUG("DEBUG");

    companion object {
        fun byLabel(label: String): LogLevel? = entries.firstOrNull { it.label == label }
    }
}

/**
 * One line logged by this process, as [LogBook] keeps it in memory
 * @param sequence Counts up from 1 for each line since the process started, what [LogTail] reads after
 * @param time Epoch milliseconds
 * @param message With the stack trace after it on its own lines, for an error that had one
 */
@Serializable
data class LogLine(
    val sequence: Long,
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

/**
 * How log files are kept, on the server (an admin sets them) and on each device (its own Settings)
 * @param keepFiles Newest files kept, older ones are deleted. A file is one day unless [rolloverMinute] moved
 * @param maxTotalMb The most all the files can take together, the oldest go first past it. A single file reaching it
 * stops being written until the next one starts
 * @param rolloverMinute Minutes after midnight, local time, when a new file starts. 0 is midnight
 */
@Serializable
data class LogSettings(
    val keepFiles: Int = DEFAULT_KEEP_FILES,
    val maxTotalMb: Int = DEFAULT_MAX_TOTAL_MB,
    val rolloverMinute: Int = 0
) {
    /** Clamped into the ranges, for settings typed or sent from elsewhere */
    fun normalized(): LogSettings = LogSettings(
        keepFiles = keepFiles.coerceIn(KEEP_FILES_RANGE),
        maxTotalMb = maxTotalMb.coerceIn(MAX_TOTAL_MB_RANGE),
        rolloverMinute = rolloverMinute.coerceIn(0, MINUTES_IN_DAY - 1)
    )

    val maxTotalBytes: Long get() = maxTotalMb.toLong() * 1024 * 1024

    /** The rollover time like 06:30 */
    val rolloverText: String get() = formatMinuteOfDay(rolloverMinute)

    companion object {
        const val DEFAULT_KEEP_FILES = 14
        const val DEFAULT_MAX_TOTAL_MB = 100
        const val MINUTES_IN_DAY = 24 * 60
        val KEEP_FILES_RANGE = 1..365
        val MAX_TOTAL_MB_RANGE = 1..10_000

        /** `HH:mm` as minutes after midnight, null for anything else */
        fun parseMinuteOfDay(text: String): Int? {
            val parts = text.trim().split(":")
            if (parts.size != 2) return null
            val hours = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val minutes = parts[1].takeIf { it.length == 2 }?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return hours * 60 + minutes
        }

        fun formatMinuteOfDay(minute: Int): String =
            "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
    }
}

/**
 * A log file kept on disk
 * @param name Like `ticketman-2026-11-06_0000.log`, named for when it started
 * @param startedAt Epoch milliseconds
 * @param current Still being written
 */
@Serializable
data class LogFileInfo(
    val name: String,
    val startedAt: Long,
    val sizeBytes: Long,
    val current: Boolean = false
)

/**
 * The server's logs as the admin Logs panel shows them
 * @param storing False when the server has nowhere to write files, its lines are only kept in memory
 */
@Serializable
data class LogOverview(
    val settings: LogSettings,
    val files: List<LogFileInfo>,
    val storing: Boolean
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * Lines logged after a sequence number, for following the log live
 * @param run Changes each time the process starts, so a reader starts over when the sequence numbers do
 * @param last The newest line's sequence, what to ask after next time
 */
@Serializable
data class LogTail(
    val run: String,
    val lines: List<LogLine>,
    val last: Long
)

package org.mr0btain.ticketman.logging

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How log lines are written to a file and read back: `2026-11-06 14:03:22.123 ERROR [Tag] message`, in the local
 * time of the machine writing it. A message's later lines (a stack trace) are indented, so only a line's first
 * line starts with a time
 */
object LogFormat {
    private const val CONTINUATION = "    "
    private const val TIME_LENGTH = 23
    private const val FILE_PREFIX = "ticketman-"
    private const val FILE_SUFFIX = ".log"
    private val FILE_NAME = Regex("""ticketman-(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})\.log""")

    /** The line as written to a file, with no line break on the end */
    fun format(line: LogLine, zone: TimeZone): String {
        val head = "${formatTime(line.time, zone)} ${line.level.label.padEnd(5)} [${line.tag}] "
        val body = line.message.lines()
        return head + body.first() + body.drop(1).joinToString("") { "\n$CONTINUATION$it" }
    }

    /** Epoch milliseconds as `2026-11-06 14:03:22.123` */
    fun formatTime(time: Long, zone: TimeZone): String {
        val local = Instant.fromEpochMilliseconds(time).toLocalDateTime(zone)
        return "${local.date} ${two(local.hour)}:${two(local.minute)}:${two(local.second)}.${(local.nanosecond / 1_000_000).toString().padStart(3, '0')}"
    }

    /** When a file line was logged, in epoch milliseconds, or null for a message's later lines and anything else */
    fun parseTime(text: String, zone: TimeZone): Long? {
        if (text.length < TIME_LENGTH || !text[0].isDigit()) return null
        return runCatching {
            val date = LocalDate.parse(text.substring(0, 10))
            if (text[10] != ' ' || text[13] != ':' || text[16] != ':' || text[19] != '.') return null
            val time = LocalTime(
                hour = text.substring(11, 13).toInt(),
                minute = text.substring(14, 16).toInt(),
                second = text.substring(17, 19).toInt(),
                nanosecond = text.substring(20, 23).toInt() * 1_000_000
            )
            LocalDateTime(date, time).toInstant(zone).toEpochMilliseconds()
        }.getOrNull()
    }

    /** The local date and time a file holding [time] started, with [rolloverMinute] as each file's start */
    fun periodStart(time: Long, rolloverMinute: Int, zone: TimeZone): LocalDateTime {
        val instant = Instant.fromEpochMilliseconds(time)
        val local = instant.toLocalDateTime(zone)
        val at = LocalTime(rolloverMinute / 60, rolloverMinute % 60)
        val today = LocalDateTime(local.date, at)
        return if (today.toInstant(zone) <= instant) today else LocalDateTime(local.date.minus(1, DateTimeUnit.DAY), at)
    }

    /** When the file started at [start] ends and the next one starts, in epoch milliseconds */
    fun periodEnd(start: LocalDateTime, zone: TimeZone): Long =
        LocalDateTime(start.date.plus(1, DateTimeUnit.DAY), start.time).toInstant(zone).toEpochMilliseconds()

    /** `ticketman-2026-11-06_0600.log` for a file started at 06:00 on 6 November */
    fun fileName(start: LocalDateTime): String = "$FILE_PREFIX${start.date}_${two(start.hour)}${two(start.minute)}$FILE_SUFFIX"

    /** When a file named by [fileName] started, or null for a name that isn't a log file's */
    fun fileStart(name: String): LocalDateTime? {
        val match = FILE_NAME.matchEntire(name) ?: return null
        val (year, month, day, hour, minute) = match.destructured
        return runCatching { LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt()) }.getOrNull()
    }

    private fun two(value: Int) = value.toString().padStart(2, '0')
}

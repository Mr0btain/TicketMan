package org.mr0btain.ticketman.components.utils

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.roundToLong

// Turning times, sizes and enum names into text for the screen. Times are shown in this device's time zone

private val zone: TimeZone
    get() = TimeZone.currentSystemDefault()

private fun localDate(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

/** Epoch millis as "2026-10-17 14:03" */
fun formatDateTime(millis: Long): String {
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return "${time.date} ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** [formatDay] for the middle of a sentence: "due today", but still "due Fri Oct 17" */
fun formatDayInSentence(millis: Long): String =
    formatDay(millis).let { if (it in setOf("Today", "Tomorrow", "Yesterday")) it.lowercase() else it }

/** Epoch millis as "Fri Oct 17", or "Today"/"Tomorrow"/"Yesterday" when close */
fun formatDay(millis: Long): String {
    val date = localDate(millis)
    val today = Clock.System.now().toLocalDateTime(zone).date

    return when (date.toEpochDays() - today.toEpochDays()) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> {
            val day = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
            val month = date.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
            val year = if (date.year != today.year) " ${date.year}" else ""
            "$day $month ${date.day}$year"
        }
    }
}

/**
 * Epoch millis for 5pm local time [days] from today, used for promised dates
 */
fun promiseTimeInDays(days: Int): Long {
    val today = Clock.System.now().toLocalDateTime(zone).date
    val day = today.plus(DatePeriod(days = days))

    return LocalDateTime(day, LocalTime(PROMISE_HOUR, 0)).toInstant(zone).toEpochMilliseconds()
}

private const val PROMISE_HOUR = 17

/** Epoch millis as "Fri Oct 17 2026", for paper where "today" won't make sense later */
fun formatDate(millis: Long): String {
    val date = localDate(millis)
    val day = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }

    return "$day $month ${date.day} ${date.year}"
}

/** Whether the time falls on a day before today */
fun isBeforeToday(millis: Long): Boolean =
    localDate(millis) < Clock.System.now().toLocalDateTime(zone).date

/** How long ago, like "5m ago" */
fun formatTimeAgo(millis: Long, now: Long = Clock.System.now().toEpochMilliseconds()): String {
    val seconds = (now - millis) / 1000

    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/** Length of time, like "3d 4h" */
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0

    return when {
        mb >= 1 -> "${(mb * 10).toLong() / 10.0} MB"
        kb >= 1 -> "${(kb * 10).toLong() / 10.0} KB"
        else -> "$bytes B"
    }
}

/** Enum names like IN_PROGRESS as "In progress" */
fun formatEnumName(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

/** Quantities like 2 or 1.5, whole numbers without the .0 */
fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()

/** Hours worked like "1.5h", to two decimals at most */
fun formatHours(hours: Double): String =
    "${formatQuantity((hours * 100).roundToLong() / 100.0)}h"

/** A number of hours as a length of time, like "2d 3h" */
fun formatHoursAsDuration(hours: Double): String =
    formatDuration((hours * 3_600_000).roundToLong())

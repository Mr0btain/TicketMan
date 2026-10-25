package org.mr0btain.ticketman.data.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.ticket.StatusTone
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What a schedule entry is
 * @param allDay Whole days rather than hours, like time off
 * @param tone Its color everywhere it's drawn, so a kind reads the same in every theme
 */
@Serializable
enum class ScheduleKind(val label: String, val allDay: Boolean, val tone: StatusTone) {
    SHIFT("Shift", false, StatusTone.ACCENT),
    TIME_OFF("Time off", true, StatusTone.WARNING),
    TRAINING("Training", false, StatusTone.SUCCESS),
    OTHER("Other", false, StatusTone.NEUTRAL)
}

/**
 * Someone on the staff schedule: a shift with its hours, or time off, training or anything else, from [startsAt]
 * up to [endsAt]. Whole day ones run from the start of their first day to the start of the day after their last
 * @param userName Filled in by the server
 * @param allDay Whole days: [startsAt] and [endsAt] are midnights in the shop's time zone
 * @param seriesIdentifier Shared by the entries one weekly repeat made, null for one on its own
 * @param locationIdentifier Where it is, set by the server from where it was written when it isn't given
 * @param createdBy Username of the admin who wrote it, set by the server
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class ScheduleEntry(
    val identifier: Uuid = Uuid.generateV7(),
    val userIdentifier: Uuid,
    val userName: String = "",
    val kind: ScheduleKind = ScheduleKind.SHIFT,
    val startsAt: Long,
    val endsAt: Long,
    val allDay: Boolean = kind.allDay,
    val note: String = "",
    val seriesIdentifier: Uuid? = null,
    val locationIdentifier: Uuid? = null,
    val createdBy: String = "",
    val createdAt: Long = 0
) {
    val millis: Long
        get() = (endsAt - startsAt).coerceAtLeast(0)

    /** Hours worked for a timed entry, none for a whole day one */
    val hours: Double
        get() = if (allDay) 0.0 else millis / ScheduleRules.HOUR_MILLIS

    /** The days it's on in [zone], first to last */
    fun days(zone: TimeZone): List<LocalDate> {
        val first = Instant.fromEpochMilliseconds(startsAt).toLocalDateTime(zone).date
        // The end is exclusive, so a shift ending at midnight or a whole day ending at the next midnight stays put
        val last = Instant.fromEpochMilliseconds((endsAt - 1).coerceAtLeast(startsAt)).toLocalDateTime(zone).date
        return generateSequence(first) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= last }.toList()
    }

    /** Whether any of it falls on [day] in [zone] */
    fun isOn(day: LocalDate, zone: TimeZone): Boolean {
        val start = day.atStartOfDayIn(zone).toEpochMilliseconds()
        val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
        return startsAt < end && endsAt > start
    }

    /** What's wrong with it as typed, null when it's fine */
    fun problem(): String? = when {
        startsAt <= 0 -> "Pick when it starts"
        endsAt <= startsAt -> if (allDay) "The last day can't be before the first" else "It has to end after it starts"
        !allDay && millis > ScheduleRules.MAX_SHIFT_MILLIS -> "A shift can be up to ${ScheduleRules.MAX_SHIFT_HOURS} hours, use whole days for longer"
        allDay && millis > ScheduleRules.MAX_DAYS * ScheduleRules.DAY_MILLIS + ScheduleRules.HOUR_MILLIS -> "Up to ${ScheduleRules.MAX_DAYS} days at once"
        note.length > ScheduleRules.MAX_NOTE -> "The note is too long"
        else -> null
    }
}

/**
 * Repeats a new entry each week on [weekdays] until [until]
 * @param weekdays ISO day numbers, 1 Monday to 7 Sunday
 * @param until The last day it can fall on, like 2026-12-31
 */
@Serializable
data class ScheduleRepeat(
    val weekdays: List<Int>,
    val until: String
)

/**
 * A new entry, maybe repeating
 * @param repeat Each week on some days until a date, the entry being the first. Null for just the one
 */
@Serializable
data class ScheduleRequest(
    val entry: ScheduleEntry,
    val repeat: ScheduleRepeat? = null
)

/** Which entries of a series a change or removal goes to */
@Serializable
enum class ScheduleScope(val label: String) {
    ONE("Just this one"),
    FOLLOWING("This and later ones"),
    SERIES("The whole series")
}

object ScheduleRules {
    const val HOUR_MILLIS = 3_600_000.0
    const val DAY_MILLIS = 86_400_000L
    const val MAX_SHIFT_HOURS = 24
    const val MAX_SHIFT_MILLIS = MAX_SHIFT_HOURS * 3_600_000L
    const val MAX_DAYS = 366
    const val MAX_NOTE = 500

    /** Most entries one repeat makes, about a year of every day */
    const val MAX_REPEATS = 370

    /** Longest range read at once */
    const val MAX_RANGE_DAYS = 93

    /**
     * The entries [request] makes in [zone]: its entry, and with a repeat a copy on each chosen weekday from its
     * first day to the repeat's last, at the same time of day for the same length, all in one series. Null when
     * the repeat isn't one: no weekdays, a last day before the first, or more than [MAX_REPEATS]
     */
    @OptIn(ExperimentalUuidApi::class)
    fun expand(request: ScheduleRequest, zone: TimeZone): List<ScheduleEntry>? {
        val entry = request.entry
        val repeat = request.repeat ?: return listOf(entry)
        val until = runCatching { LocalDate.parse(repeat.until.trim()) }.getOrNull() ?: return null
        val weekdays = repeat.weekdays.filter { it in 1..7 }.toSet()
        if (weekdays.isEmpty()) return null

        val start = Instant.fromEpochMilliseconds(entry.startsAt).toLocalDateTime(zone)
        if (until < start.date) return null
        val length = entry.millis
        val series = entry.identifier

        val days = generateSequence(start.date) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= until }
            .filter { it.dayOfWeek.ordinal + 1 in weekdays }
            .toList()
        if (days.isEmpty() || days.size > MAX_REPEATS) return null

        return days.mapIndexed { index, day ->
            val startsAt = kotlinx.datetime.LocalDateTime(day, start.time).toInstant(zone).toEpochMilliseconds()
            entry.copy(
                identifier = if (index == 0) entry.identifier else Uuid.generateV7(),
                startsAt = startsAt,
                endsAt = startsAt + length,
                seriesIdentifier = series
            )
        }
    }

    /** Scheduled hours per day for [entries] in [zone], each counting on the day it starts */
    fun hoursByDay(entries: List<ScheduleEntry>, zone: TimeZone): Map<LocalDate, Double> =
        entries.filter { !it.allDay }
            .groupBy { Instant.fromEpochMilliseconds(it.startsAt).toLocalDateTime(zone).date }
            .mapValues { (_, list) -> list.sumOf { it.hours } }

    /** "8h", "7.5h" */
    fun hoursText(hours: Double): String =
        if (hours % 1.0 == 0.0) "${hours.toInt()}h" else "${(hours * 10).toLong() / 10.0}h"
}

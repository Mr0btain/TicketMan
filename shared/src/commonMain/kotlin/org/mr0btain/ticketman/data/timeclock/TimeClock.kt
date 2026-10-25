package org.mr0btain.ticketman.data.timeclock

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One stretch on the clock: clocked in, and out again once [clockedOutAt] is set
 * @param userName Filled in by the server
 * @param clockedInAt Epoch millis
 * @param clockedOutAt Epoch millis, null while still on the clock
 * @param station Where they clocked in, a station's name, or blank from their own device
 * @param editedBy Username of the admin who last changed it by hand, blank for one made by clocking
 * @param locationIdentifier The location they clocked in at, set by the server
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TimeEntry(
    val identifier: Uuid = Uuid.generateV7(),
    val userIdentifier: Uuid,
    val userName: String = "",
    val clockedInAt: Long,
    val clockedOutAt: Long? = null,
    val station: String = "",
    val note: String = "",
    val editedBy: String = "",
    val locationIdentifier: Uuid? = null
) {
    val isOpen: Boolean
        get() = clockedOutAt == null

    /** How long, up to [now] while still on the clock */
    fun millis(now: Long): Long = ((clockedOutAt ?: now) - clockedInAt).coerceAtLeast(0)

    fun hours(now: Long): Double = millis(now) / TimeClockRules.HOUR_MILLIS

    /** What's wrong with it as an admin typed it, null when it's fine */
    fun problem(): String? = when {
        clockedInAt <= 0 -> "Enter when they clocked in"
        clockedOutAt != null && clockedOutAt <= clockedInAt -> "Clocking out has to come after clocking in"
        clockedOutAt != null && clockedOutAt - clockedInAt > TimeClockRules.MAX_SHIFT_MILLIS -> "A shift can be up to ${TimeClockRules.MAX_SHIFT_HOURS} hours"
        note.length > TimeClockRules.MAX_NOTE -> "The note is too long"
        else -> null
    }
}

/**
 * What clocking did
 * @param clockedIn True when it started [entry], false when it ended it
 */
@Serializable
data class PunchResult(
    val entry: TimeEntry,
    val clockedIn: Boolean
)

/** Whether the caller is on the clock now */
@Serializable
data class ClockState(val entry: TimeEntry? = null)

/**
 * Time on the clock from one day to another, both included, by when each stretch started
 * @param people Each person with time in those days, by name
 */
@Serializable
data class Timesheet(
    val from: String,
    val to: String,
    val people: List<TimesheetPerson> = emptyList(),
    val generatedAt: Long = 0
)

/**
 * One person's time
 * @param hours Their entries added up, open ones up to [Timesheet.generatedAt]
 * @param entries Oldest first
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TimesheetPerson(
    val userIdentifier: Uuid,
    val name: String,
    val hours: Double,
    val entries: List<TimeEntry>
)

object TimeClockRules {
    const val HOUR_MILLIS = 3_600_000.0
    const val MAX_SHIFT_HOURS = 24
    const val MAX_SHIFT_MILLIS = MAX_SHIFT_HOURS * 3_600_000L
    const val MAX_NOTE = 500

    /** The most days one timesheet covers */
    const val MAX_DAYS = 92
}

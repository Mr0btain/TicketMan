package org.mr0btain.ticketman.data.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class TimeOffStatus(val label: String) {
    PENDING("Waiting"),
    APPROVED("Approved"),
    DECLINED("Declined"),

    /** Taken back by whoever asked, before it was answered */
    WITHDRAWN("Withdrawn")
}

/**
 * Someone asking for time off: whole days from [startsAt] to [endsAt] (midnight to midnight, like a time off
 * entry), taking [hours] from their allowance. Approving it puts a time off entry on the schedule
 * @param hours What it takes from the allowance, like 8 a day, or fewer for part of a day
 * @param userName Filled in by the server
 * @param reason Why it was declined
 * @param entryIdentifier The schedule entry approving it made
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TimeOffRequest(
    val identifier: Uuid = Uuid.generateV7(),
    val userIdentifier: Uuid? = null,
    val userName: String = "",
    val startsAt: Long,
    val endsAt: Long,
    val hours: Double,
    val note: String = "",
    val status: TimeOffStatus = TimeOffStatus.PENDING,
    val createdAt: Long = 0,
    val decidedBy: String = "",
    val decidedAt: Long? = null,
    val reason: String = "",
    val entryIdentifier: Uuid? = null
) {
    /** What's wrong with it as asked, null when it's fine */
    fun problem(): String? = when {
        startsAt <= 0 -> "Pick the first day"
        endsAt <= startsAt -> "The last day can't be before the first"
        endsAt - startsAt > TimeOffRules.MAX_DAYS * ScheduleRules.DAY_MILLIS + ScheduleRules.HOUR_MILLIS -> "Ask for up to ${TimeOffRules.MAX_DAYS} days at once"
        hours <= 0 -> "Say how many hours it takes"
        hours > (endsAt - startsAt) / ScheduleRules.HOUR_MILLIS -> "That's more hours than the days have"
        note.length > ScheduleRules.MAX_NOTE -> "The note is too long"
        else -> null
    }

    /** The year whose allowance it comes from, the one its first day is in */
    fun year(zone: TimeZone): Int = Instant.fromEpochMilliseconds(startsAt).toLocalDateTime(zone).year
}

/**
 * Someone's time off for a year: what they're allowed, what's been approved and what's waiting
 * @param allowanceHours Set by an admin, the same every year. 0 until it's set
 * @param takenHours Time off entries starting in the year
 * @param pendingHours Requests in the year still waiting
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TimeOffBalance(
    val userIdentifier: Uuid,
    val userName: String = "",
    val year: Int,
    val allowanceHours: Double = 0.0,
    val takenHours: Double = 0.0,
    val pendingHours: Double = 0.0
) {
    /** What can still be asked for */
    val leftHours: Double
        get() = (allowanceHours - takenHours - pendingHours).coerceAtLeast(0.0)
}

/** Someone's requests and balance, for their own view */
@Serializable
data class MyTimeOff(
    val balance: TimeOffBalance,
    val requests: List<TimeOffRequest> = emptyList()
)

/** An admin's answer to a request */
@Serializable
data class TimeOffDecision(val reason: String = "")

/** An admin setting someone's yearly allowance */
@Serializable
data class TimeOffAllowance(val hours: Double)

object TimeOffRules {
    const val MAX_DAYS = 60
    const val MAX_ALLOWANCE_HOURS = 2_000.0

    /** Hours taken for each whole day off unless said otherwise */
    const val HOURS_PER_DAY = 8.0

    /** The start of [year] in [zone], for counting a year's time off */
    fun yearStart(year: Int, zone: TimeZone): Long = LocalDate(year, 1, 1).atStartOfDayIn(zone).toEpochMilliseconds()

    /** Hours a whole day request takes by default: [HOURS_PER_DAY] for each weekday in it */
    fun defaultHours(first: LocalDate, last: LocalDate): Double {
        var day = first
        var weekdays = 0
        while (day <= last) {
            if (day.dayOfWeek.ordinal < 5) weekdays++
            day = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        }
        return weekdays * HOURS_PER_DAY
    }
}

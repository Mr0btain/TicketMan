package org.mr0btain.ticketman.data.appointment

import kotlinx.serialization.Serializable

/**
 * Customers booking a visit themselves from the status page, into the times staff are scheduled to work
 * @param minutes How long each visit is booked for, and so how far apart the times offered are
 * @param daysAhead How many days ahead are offered, today included
 * @param leadMinutes How soon from now the first time offered can be
 * @param note Shown above the times, like what to bring
 */
@Serializable
data class OnlineBooking(
    val enabled: Boolean = false,
    val minutes: Int = AppointmentRules.DEFAULT_MINUTES,
    val daysAhead: Int = 14,
    val leadMinutes: Int = 120,
    val note: String = ""
) {
    /** What's wrong with these settings, or null */
    fun problem(): String? = when {
        minutes !in BookingRules.MINUTES -> "A booked visit is ${BookingRules.MINUTES.first} to ${BookingRules.MINUTES.last} minutes"
        daysAhead !in 1..BookingRules.MAX_DAYS_AHEAD -> "Offer 1 to ${BookingRules.MAX_DAYS_AHEAD} days ahead"
        leadMinutes !in 0..BookingRules.MAX_LEAD_MINUTES -> "The first time offered is up to ${BookingRules.MAX_LEAD_MINUTES / 60} hours away"
        note.length > BookingRules.MAX_NOTE -> "The note is too long"
        else -> null
    }
}

object BookingRules {
    val MINUTES = 10..240
    const val MAX_DAYS_AHEAD = 60
    const val MAX_LEAD_MINUTES = 7 * 24 * 60
    const val MAX_NOTE = 500
    const val MAX_FIELD = 200
    const val MAX_PROBLEM = 1000

    /**
     * The times that can still be booked, earliest first: every [minutes] from midnight within someone's shift, the
     * whole visit inside it, from [earliest] up to [latest]. Each shift takes one visit at a time, so a time is
     * open while fewer visits overlap it than shifts cover it
     * @param shifts Each person's working stretches, start and end
     * @param booked Visits already booked, start and end
     * @param midnightOf The start of the day a time is in, in the shop's time zone
     */
    fun openTimes(
        shifts: List<Pair<Long, Long>>,
        booked: List<Pair<Long, Long>>,
        minutes: Int,
        earliest: Long,
        latest: Long,
        midnightOf: (Long) -> Long
    ): List<Long> {
        val step = minutes * 60_000L
        val candidates = mutableSetOf<Long>()
        shifts.forEach { (start, end) ->
            val midnight = midnightOf(start)
            var time = midnight + ((start - midnight + step - 1) / step) * step
            while (time + step <= end) {
                if (time >= earliest && time < latest) candidates += time
                time += step
            }
        }

        return candidates.sorted().filter { time ->
            val covering = shifts.count { (start, end) -> start <= time && end >= time + step }
            val taken = booked.count { (start, end) -> start < time + step && end > time }
            taken < covering
        }
    }
}

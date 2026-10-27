package org.mr0btain.ticketman.data.appointment

import kotlin.test.Test
import kotlin.test.assertEquals

class BookingRulesTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    // A day starting at 0, so times are hours from midnight
    private fun open(shifts: List<Pair<Long, Long>>, booked: List<Pair<Long, Long>> = emptyList(), minutes: Int = 30, earliest: Long = 0) =
        BookingRules.openTimes(shifts, booked, minutes, earliest, 24 * hour) { 0 }.map { it / minute }

    @Test
    fun timesFitInsideShiftsOnTheClock() {
        // 9:10 to 11:00: from 9:30, the last one ending at 11:00
        assertEquals(listOf(570L, 600L, 630L), open(listOf(9 * hour + 10 * minute to 11 * hour)))
    }

    @Test
    fun eachPersonWorkingTakesOneVisitAtATime() {
        val two = listOf(9 * hour to 11 * hour, 9 * hour to 10 * hour)
        // One booked at 9:00: still a place then, since two are working
        assertEquals(listOf(540L, 570L, 600L, 630L), open(two, booked = listOf(9 * hour to 9 * hour + 30 * minute)))
        // Two booked at 9:00 fill it; a long one over 10:00 takes the only place there is then
        assertEquals(listOf(570L, 630L), open(two, booked = listOf(9 * hour to 9 * hour + 30 * minute, 9 * hour to 9 * hour + 30 * minute, 10 * hour to 10 * hour + 30 * minute)))
    }

    @Test
    fun notBeforeTheEarliest() {
        assertEquals(listOf(600L, 630L), open(listOf(9 * hour to 11 * hour), earliest = 9 * hour + 31 * minute))
    }
}

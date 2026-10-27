package org.mr0btain.ticketman.data.timeclock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ShiftCheckTest {
    private val hour = 3_600_000L
    private val minute = 60_000L
    private val person = Uuid.random()

    private fun clockedIn(at: Long) = TimeEntry(userIdentifier = person, clockedInAt = at, clockedOutAt = at + 8 * hour)

    @Test
    fun clockingInIsOnTimeLateOrMissed() {
        val day = 100 * 24 * hour
        val shifts = listOf(
            day + 9 * hour to day + 17 * hour,
            day + 33 * hour to day + 41 * hour,
            day + 57 * hour to day + 65 * hour,
            day + 81 * hour to day + 89 * hour
        )
        val entries = listOf(
            clockedIn(day + 9 * hour + 3 * minute), // within the grace
            clockedIn(day + 33 * hour + 20 * minute) // 20 minutes late
        )
        // Now is in the third shift's grace window's end past, before the fourth starts
        val checks = TimeClockRules.check(shifts, entries, now = day + 70 * hour)

        assertEquals(0, checks[0].lateMinutes)
        assertEquals(20, checks[1].lateMinutes)
        assertTrue(checks[2].missed)
        assertNull(checks[2].clockedInAt)
        assertFalse(checks[3].missed, "still to come")
        assertNull(checks[3].lateMinutes)
    }

    @Test
    fun earlyClockInsCountButNotTheDayBefore() {
        val start = 500 * hour
        val shift = listOf(start to start + 8 * hour)

        assertEquals(0, TimeClockRules.check(shift, listOf(clockedIn(start - 30 * minute)), now = start + hour).single().lateMinutes)
        assertTrue(TimeClockRules.check(shift, listOf(clockedIn(start - 5 * hour)), now = start + hour).single().missed)
    }
}

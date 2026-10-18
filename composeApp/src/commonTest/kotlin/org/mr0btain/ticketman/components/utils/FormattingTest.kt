package org.mr0btain.ticketman.components.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.*
import kotlin.time.Instant

class FormattingTest {
    private val zone = TimeZone.currentSystemDefault()
    private fun local(text: String) = LocalDateTime.parse(text).toInstant(zone).toEpochMilliseconds()

    @Test
    fun dateTimesAreInLocalTime() {
        assertEquals("2026-03-07 09:05", formatDateTime(local("2026-03-07T09:05:59")))
        assertEquals("Sat Mar 7 2026", formatDate(local("2026-03-07T23:59")))
    }

    @Test
    fun closeDaysAreSaidPlainly() {
        assertEquals("Today", formatDay(promiseTimeInDays(0)))
        assertEquals("Tomorrow", formatDay(promiseTimeInDays(1)))
        assertEquals("Yesterday", formatDay(promiseTimeInDays(-1)))
        assertTrue(formatDay(local("2001-01-01T12:00")).endsWith(" 2001"))
    }

    @Test
    fun promisesAreFivePmOnTheDay() {
        val promised = Instant.fromEpochMilliseconds(promiseTimeInDays(3)).toLocalDateTime(zone)

        assertEquals(17, promised.hour)
        assertEquals(0, promised.minute)
        assertFalse(isBeforeToday(promiseTimeInDays(0)))
        assertTrue(isBeforeToday(promiseTimeInDays(-1)))
    }

    @Test
    fun timeAgoRoundsDown() {
        val now = 1_000_000_000L

        assertEquals("just now", formatTimeAgo(now - 59_000, now))
        assertEquals("1m ago", formatTimeAgo(now - 60_000, now))
        assertEquals("59m ago", formatTimeAgo(now - 3_599_000, now))
        assertEquals("1h ago", formatTimeAgo(now - 3_600_000, now))
        assertEquals("23h ago", formatTimeAgo(now - 86_399_000, now))
        assertEquals("2d ago", formatTimeAgo(now - 2 * 86_400_000L, now))
    }

    @Test
    fun durations() {
        assertEquals("0m", formatDuration(59_000))
        assertEquals("45m", formatDuration(45 * 60_000L))
        assertEquals("1h 30m", formatDuration(90 * 60_000L))
        assertEquals("3d 4h", formatDuration((3 * 24 + 4) * 3_600_000L + 59 * 60_000L))
    }

    @Test
    fun sizes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1_536))
        assertEquals("25.0 MB", formatBytes(25L * 1024 * 1024))
        assertEquals("1.9 MB", formatBytes(1_999_999))
    }

    @Test
    fun enumNamesAndQuantities() {
        assertEquals("In progress", formatEnumName("IN_PROGRESS"))
        assertEquals("Done", formatEnumName("DONE"))
        assertEquals("2", formatQuantity(2.0))
        assertEquals("1.5", formatQuantity(1.5))
    }
}

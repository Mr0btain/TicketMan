package org.mr0btain.ticketman.logging

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.readString
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogFileStoreTest {
    private val zone = TimeZone.UTC
    private lateinit var folder: File

    @BeforeTest
    fun setUp() {
        folder = Files.createTempDirectory("ticketman-logs").toFile()
    }

    @AfterTest
    fun tearDown() {
        LogBook.reset()
        folder.deleteRecursively()
    }

    private fun at(text: String): Long = LocalDateTime.parse(text).toInstant(zone).toEpochMilliseconds()

    private fun line(time: Long, message: String, level: LogLevel = LogLevel.INFO, sequence: Long = 1) =
        LogLine(sequence, time, level, "Test", message)

    private fun store(settings: LogSettings = LogSettings()) = LogFileStore(Path(folder.path), settings, zone = { zone })

    private fun names() = folder.list()!!.sorted()

    @Test
    fun formatAndParseRoundTrip() {
        val time = at("2026-11-06T14:03:22.123")
        val text = LogFormat.format(line(time, "first\nsecond"), zone)

        assertEquals("2026-11-06 14:03:22.123 INFO  [Test] first\n    second", text)
        assertEquals(time, LogFormat.parseTime(text, zone))
        assertNull(LogFormat.parseTime("    second", zone))
        assertNull(LogFormat.parseTime("garbage", zone))
    }

    @Test
    fun periodStartsAtTheRolloverTime() {
        // 06:30 rollover: 05:00 belongs to yesterday's file, 07:00 to today's
        assertEquals(LocalDateTime.parse("2026-11-05T06:30"), LogFormat.periodStart(at("2026-11-06T05:00"), 6 * 60 + 30, zone))
        assertEquals(LocalDateTime.parse("2026-11-06T06:30"), LogFormat.periodStart(at("2026-11-06T07:00"), 6 * 60 + 30, zone))
        assertEquals(LocalDateTime.parse("2026-11-06T06:30"), LogFormat.periodStart(at("2026-11-06T06:30"), 6 * 60 + 30, zone))

        val name = LogFormat.fileName(LocalDateTime.parse("2026-11-06T06:30"))
        assertEquals("ticketman-2026-11-06_0630.log", name)
        assertEquals(LocalDateTime.parse("2026-11-06T06:30"), LogFormat.fileStart(name))
        assertNull(LogFormat.fileStart("settings.json"))
    }

    @Test
    fun startsANewFileAtTheRolloverTime() {
        val store = store(LogSettings(rolloverMinute = 6 * 60))
        store.write(line(at("2026-11-06T05:59:59"), "before"))
        store.write(line(at("2026-11-06T06:00"), "after"))
        store.close()

        assertEquals(listOf("ticketman-2026-11-05_0600.log", "ticketman-2026-11-06_0600.log"), names())
        assertTrue(File(folder, "ticketman-2026-11-05_0600.log").readText().contains("before"))
        assertTrue(File(folder, "ticketman-2026-11-06_0600.log").readText().contains("after"))
    }

    @Test
    fun keepsOnlyTheNewestFiles() {
        val store = store(LogSettings(keepFiles = 2))
        listOf("01", "02", "03", "04").forEach { day -> store.write(line(at("2026-11-${day}T12:00"), "day $day")) }
        store.close()

        assertEquals(listOf("ticketman-2026-11-03_0000.log", "ticketman-2026-11-04_0000.log"), names())
    }

    @Test
    fun trimsOldFilesPastTheSizeLimitAndStopsAFullOne() {
        val big = "x".repeat(400_000)
        val store = store(LogSettings(maxTotalMb = 1))
        store.write(line(at("2026-11-01T12:00"), big))
        store.write(line(at("2026-11-02T12:00"), big))
        store.write(line(at("2026-11-03T12:00"), big))
        // 1.2 MB together, so the first day's goes
        assertEquals(listOf("ticketman-2026-11-02_0000.log", "ticketman-2026-11-03_0000.log"), names())

        // Today's alone past the limit: a notice, then nothing until tomorrow
        store.write(line(at("2026-11-03T12:01"), big))
        store.write(line(at("2026-11-03T12:02"), big))
        store.write(line(at("2026-11-03T12:03"), "lost"))
        store.close()
        val today = File(folder, "ticketman-2026-11-03_0000.log").readText()
        assertTrue(today.contains("reached the 1 MB limit"))
        assertFalse(today.contains("lost"))
        assertTrue(File(folder, "ticketman-2026-11-03_0000.log").length() <= 1024 * 1024 + 300)
    }

    @Test
    fun exportsARangeWithStackTracesKept() {
        val store = store()
        store.write(line(at("2026-11-05T09:00"), "too early"))
        store.write(line(at("2026-11-06T09:00"), "wanted\njava.lang.Exception\n\tat Somewhere", LogLevel.ERROR))
        store.write(line(at("2026-11-06T10:00"), "also wanted"))
        store.write(line(at("2026-11-07T09:00"), "too late"))
        store.flush()

        val out = Buffer()
        val count = store.export(store.files(), at("2026-11-06T00:00"), at("2026-11-06T23:59:59"), out)
        val text = out.readString()

        assertEquals(2, count)
        assertTrue(text.contains("wanted\n    java.lang.Exception\n    \tat Somewhere"))
        assertTrue(text.contains("also wanted"))
        assertFalse(text.contains("too early"))
        assertFalse(text.contains("too late"))

        val all = Buffer()
        assertEquals(4, store.export(store.files(), null, null, all))
        store.close()
    }

    @Test
    fun logBookWritesWhatCameBeforeTheStoreAndFollowsSettings() {
        LogBook.reset()
        LogBook.record(LogLevel.INFO, "Early", "before the store")
        val store = store()
        LogBook.attach(store)
        LogBook.record(LogLevel.ERROR, "Late", "after", IllegalStateException("boom"))

        val tail = LogBook.since(0)
        assertEquals(2, tail.lines.size)
        assertEquals(1, LogBook.since(tail.lines.first().sequence).lines.size)

        val out = Buffer()
        assertEquals(2, LogBook.export(null, null, out))
        val text = out.readString()
        assertTrue(text.contains("before the store"))
        assertTrue(text.contains("IllegalStateException: boom"))

        LogBook.updateSettings(LogSettings(keepFiles = 3, maxTotalMb = 5, rolloverMinute = 90))
        assertEquals(LogSettings(3, 5, 90), LogBook.settings)
        assertNotNull(LogBook.files().singleOrNull())
    }

    @Test
    fun settingsParseAndClamp() {
        assertEquals(390, LogSettings.parseMinuteOfDay("06:30"))
        assertEquals(0, LogSettings.parseMinuteOfDay("0:00"))
        assertNull(LogSettings.parseMinuteOfDay("24:00"))
        assertNull(LogSettings.parseMinuteOfDay("6:3"))
        assertEquals("06:30", LogSettings(rolloverMinute = 390).rolloverText)
        assertEquals(LogSettings(1, 10_000, 1439), LogSettings(0, 99_999, 5000).normalized())
    }
}

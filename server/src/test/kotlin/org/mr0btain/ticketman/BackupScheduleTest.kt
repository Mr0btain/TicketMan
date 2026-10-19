package org.mr0btain.ticketman

import kotlinx.coroutines.runBlocking
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.BackupSchedule
import org.mr0btain.ticketman.components.service.BackupService
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.days

/**
 * The daily backup into `BACKUP_DIR`
 */
class BackupScheduleTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun oneBackupADayAndOnlyTheNewestAreKept() = runBlocking {
        val database = LocalDatabase("$dataDir/server").also { it.initDatabase() }
        val folder = File(dataDir.toFile(), "daily")
        val unrelated = File(folder.apply { mkdirs() }, "notes.txt").apply { writeText("keep me") }
        val service = BackupService(
            database, database.imageDatabase, database.imageStore, File(dataDir.toFile(), "server/backups"),
            schedule = BackupSchedule(folder = folder.path, keep = 2, includeImages = false),
            auditLog = database.auditLogDatabase
        )

        val start = System.currentTimeMillis()
        assertTrue(service.runSchedule(now = start))
        assertFalse(service.runSchedule(now = start + 1000), "Not again the same day")

        assertTrue(service.runSchedule(now = start + 1.days.inWholeMilliseconds))
        assertTrue(service.runSchedule(now = start + 2.days.inWholeMilliseconds))

        val backups = folder.listFiles().orEmpty().filter { it.name.startsWith("ticketman-backup-") }.map { it.name }.sorted()
        assertEquals(listOf("ticketman-backup-${start + 1.days.inWholeMilliseconds}-database.zip", "ticketman-backup-${start + 2.days.inWholeMilliseconds}-database.zip"), backups)
        assertTrue(unrelated.exists())
        assertTrue(folder.listFiles().orEmpty().none { it.name.startsWith(".") }, "No work files left behind")

        val info = assertNotNull(service.scheduleInfo())
        assertEquals(start + 2.days.inWholeMilliseconds, info.lastBackupAt)
        assertNull(info.lastFailedAt)
        assertEquals(3, database.auditLogDatabase.count())
    }

    @Test
    fun offWithoutAFolder() = runBlocking {
        val database = LocalDatabase("$dataDir/server").also { it.initDatabase() }
        val service = BackupService(database, database.imageDatabase, database.imageStore, File(dataDir.toFile(), "server/backups"))

        assertFalse(service.runSchedule())
        assertNull(service.scheduleInfo())
    }
}

package org.mr0btain.ticketman

import kotlinx.coroutines.runBlocking
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.BackupService
import java.io.File
import kotlin.test.*

/**
 * A backup's download link is a login in itself, so it stops working soon after it's been used
 */
class BackupLinkTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun aUsedLinkOnlyLastsLongEnoughToResume() = runBlocking {
        val database = LocalDatabase("$dataDir/server").also { it.initDatabase() }
        val service = BackupService(database, database.imageDatabase, database.imageStore, File(dataDir.toFile(), "server/backups"))

        val ticket = assertNotNull(service.prepare(includeImages = false))
        val token = ticket.downloadPath.substringAfter("token=")
        val made = ticket.expiresAt - BackupService.DOWNLOAD_WINDOW.inWholeMilliseconds
        val resume = BackupService.RESUME_WINDOW.inWholeMilliseconds

        // Unused, it lasts the whole window
        assertNotNull(service.fileFor(ticket.id, token, now = made + resume * 2))

        // Each use gives it a few more minutes from then, not the rest of the window
        val firstUse = made + resume * 2
        assertNotNull(service.fileFor(ticket.id, token, now = firstUse + resume - 1))
        val resumed = firstUse + resume - 1
        assertNull(service.fileFor(ticket.id, token, now = resumed + resume + 1))
    }
}

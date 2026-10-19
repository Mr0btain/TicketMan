package org.mr0btain.ticketman

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.Housekeeping
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.shared.PagedRequest
import kotlin.test.*
import kotlin.time.Duration.Companion.days

/**
 * Audit entries go with the change they describe, and old ones are trimmed
 */
class AuditLogTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun anEntryInsideARolledBackChangeIsntKept() {
        val database = LocalDatabase("$dataDir/server").also { it.initDatabase() }
        val auditLog = database.auditLogDatabase

        assertFails {
            transaction {
                auditLog.log(null, AuditAction.UPDATE, "ticket", details = "Never happened", actorName = "test")
                error("The change failed")
            }
        }
        auditLog.log(null, AuditAction.UPDATE, "ticket", details = "Did happen", actorName = "test")

        assertEquals(listOf("Did happen"), auditLog.getPagedItems(PagedRequest(0, 10))!!.map { it.details })
    }

    @Test
    fun entriesPastTheRetentionAreRemoved() {
        val database = LocalDatabase("$dataDir/server").also { it.initDatabase() }
        val auditLog = database.auditLogDatabase
        auditLog.log(null, AuditAction.UPDATE, "ticket", details = "Now", actorName = "test")

        val now = System.currentTimeMillis()
        val housekeeping = Housekeeping(auditLogDays = 30)

        assertEquals(0, auditLog.removeOlderThan(housekeeping.auditCutoff(now)!!))
        assertEquals(1, auditLog.removeOlderThan(housekeeping.auditCutoff(now + 31.days.inWholeMilliseconds)!!))
        assertEquals(0, auditLog.count())

        assertNull(Housekeeping(auditLogDays = 0).auditCutoff(now), "0 keeps everything")
    }
}

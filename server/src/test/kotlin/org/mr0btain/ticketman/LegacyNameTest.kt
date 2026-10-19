package org.mr0btain.ticketman

import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.isLegacyName
import org.mr0btain.ticketman.components.database.legacyName
import org.mr0btain.ticketman.components.database.tables.DataFixTable
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import kotlin.test.*

/** Names older versions stored as `first:last`, often lowercased, are rewritten once as they read */
class LegacyNameTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun oldNamesReadAsTheyWereShown() {
        assertEquals("Jane Doe", legacyName("jane:doe"))
        assertEquals("Mary Ann Smith", legacyName("mary ann:smith"))
        assertEquals("Dana", legacyName("dana"))
        assertEquals("Jean McDonald", legacyName("Jean:McDonald"))
        assertEquals("Jane", legacyName("jane:"))
    }

    @Test
    fun onlyOldLookingNamesAreTouched() {
        assertTrue(isLegacyName("jane:doe"))
        assertTrue(isLegacyName("dana"))
        assertFalse(isLegacyName("Dana Whitfield"))
        assertFalse(isLegacyName("de la Cruz, Maria"))
        assertFalse(isLegacyName("555 0100"))
    }

    /** Like a server stopping: H2 lets go of its files */
    private fun shutDownDatabase() {
        try {
            transaction { exec("SHUTDOWN") }
        } catch (_: Exception) {
            // Committing after SHUTDOWN fails, the database is closed either way
        }
    }

    @Test
    fun theFixRunsOnceOnAnUpgradedDatabase() {
        val old = Customer(name = "jane:doe")
        val oldUser = User(username = "terry", password = "terry_pass", name = "terry", accessLevel = AccessLevel.NORMAL)

        // First run: rows written the old way, as if by an older version that never ran the fix
        testApplication {
            configureTestServer(dataDir)
            startApplication()

            assertTrue(CustomerDatabase().add(old))
            assertTrue(UserDatabase().add(oldUser))
            transaction { DataFixTable.deleteWhere { DataFixTable.name eq "legacy-names" } }
        }
        shutDownDatabase()

        val typedLater = Customer(name = "bo")

        // Second run: fixed, then a lowercase name typed after it stays as typed
        testApplication {
            configureTestServer(dataDir)
            startApplication()

            assertEquals("Jane Doe", CustomerDatabase().getById(old.identifier)?.name)
            assertEquals("Terry", UserDatabase().getById(oldUser.identifier)?.name)
            assertTrue(CustomerDatabase().add(typedLater))
        }
        shutDownDatabase()

        testApplication {
            configureTestServer(dataDir)
            startApplication()

            assertEquals("bo", CustomerDatabase().getById(typedLater.identifier)?.name)
        }
    }
}

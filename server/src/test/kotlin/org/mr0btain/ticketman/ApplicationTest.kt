package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*

@OptIn(ExperimentalPathApi::class)
class ApplicationTest {
    // Keep the test database out of the real data directory
    private val dataDir = createTempDirectory("ticketman-test")

    @AfterTest
    fun cleanup() {
        // H2 is opened with DB_CLOSE_DELAY=-1, so it stays open (and locks its files) until shut down
        try {
            transaction { exec("SHUTDOWN") }
        } catch (_: Exception) {
            // No database was opened
        }

        dataDir.deleteRecursively()
        assertFalse(dataDir.exists(), "Temp data directory was not removed: $dataDir")
    }

    @Test
    fun testRoot() = testApplication {
        environment {
            config = MapApplicationConfig(
                "env.dataPath" to dataDir.invariantSeparatorsPathString,
                "jwt.audience" to "test-audience",
                "jwt.issuer" to "http://localhost",
                "jwt.realm" to "TicketMan Test",
                "jwt.secret" to "test-secret"
            )
        }
        application {
            module()
        }

        // Root route is a health check that just responds OK
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

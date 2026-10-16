package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun testRoot() = testApplication {
        configureTestServer(dataDir)

        // Root route is a health check that just responds OK
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

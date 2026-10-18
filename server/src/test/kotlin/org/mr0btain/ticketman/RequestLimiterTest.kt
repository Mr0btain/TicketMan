package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.RequestLimiter
import org.mr0btain.ticketman.server.request.AccountRequest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class RequestLimiterTest {
    private var clock = 0L
    private val limiter = RequestLimiter(maxAttempts = 3, window = 10.minutes) { clock }

    @Test
    fun refusesOnceAKeyUsesUpItsWindow() {
        repeat(3) { assertTrue(limiter.tryAcquire("10.0.0.1")) }

        assertFalse(limiter.tryAcquire("10.0.0.1"))
        // Other keys have their own count
        assertTrue(limiter.tryAcquire("10.0.0.2"))
    }

    @Test
    fun attemptsAgeOutOfTheWindow() {
        repeat(3) {
            assertTrue(limiter.tryAcquire("10.0.0.1"))
            clock += 4.minutes.inWholeMilliseconds
        }
        // The first attempt is now 12 minutes old, so there's room for one more
        assertTrue(limiter.tryAcquire("10.0.0.1"))
        assertFalse(limiter.tryAcquire("10.0.0.1"))
    }

    @Test
    fun refusedAttemptsDontCount() {
        repeat(3) { limiter.tryAcquire("10.0.0.1") }
        repeat(5) { limiter.tryAcquire("10.0.0.1") }

        clock += 10.minutes.inWholeMilliseconds + 1
        assertTrue(limiter.tryAcquire("10.0.0.1"))
    }

    @Test
    fun accountRequestsAreCappedPerAddress() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir)
            startApplication()

            val statuses = (1..6).map { n ->
                client.post("/api/auth/request-account") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(AccountRequest(username = "asker$n", password = "Bench#2026", name = "Asker:$n")))
                }.status
            }

            assertEquals(List(5) { HttpStatusCode.Accepted } + HttpStatusCode.TooManyRequests, statuses)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }
}

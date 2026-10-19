package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.RequestLimiter
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.LoginRequest
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

    private suspend fun ApplicationTestBuilder.requestFrom(forwardedFor: String, n: Int) =
        client.post("/api/auth/request-account") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", forwardedFor)
            setBody(Json.encodeToString(AccountRequest(username = "asker$n", password = "Bench#2026", name = "Asker:$n")))
        }.status

    @Test
    fun behindATrustedProxyEachClientIsCountedApart() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir, extraConfig = mapOf("env.trustProxy" to "true"))
            startApplication()

            // The proxy adds the real address last, anything before it came from the client and is ignored
            val statuses = (1..6).map { n -> requestFrom("10.9.9.$n, 203.0.113.1", n) }
            assertEquals(List(5) { HttpStatusCode.Accepted } + HttpStatusCode.TooManyRequests, statuses)

            assertEquals(HttpStatusCode.Accepted, requestFrom("203.0.113.2", 7))
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    @Test
    fun withoutTrustingAProxyTheHeaderIsIgnored() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir)
            startApplication()

            val statuses = (1..6).map { n -> requestFrom("203.0.113.$n", n) }
            assertEquals(List(5) { HttpStatusCode.Accepted } + HttpStatusCode.TooManyRequests, statuses)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    @Test
    fun checkingDoesntCountAsAnAttempt() {
        repeat(10) { assertFalse(limiter.isLimited("10.0.0.1")) }

        repeat(3) { limiter.record("10.0.0.1") }
        assertTrue(limiter.isLimited("10.0.0.1"))

        clock += 10.minutes.inWholeMilliseconds + 1
        assertFalse(limiter.isLimited("10.0.0.1"))
    }

    @Test
    fun wrongPasswordsAreCappedPerUsername() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir)
            startApplication()
            val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
            UserDatabase().add(tech)

            suspend fun login(username: String, password: String) = client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(LoginRequest(username, password)))
            }.status

            // Good logins never count
            repeat(15) { assertEquals(HttpStatusCode.Accepted, login("tech", "tech_pass")) }

            repeat(10) { assertEquals(HttpStatusCode.Unauthorized, login("Tech", "guess$it")) }

            // Now even the right password waits
            assertEquals(HttpStatusCode.TooManyRequests, login("tech", "tech_pass"))

            // The same address can still get 10 more wrong tries on another name before it's capped too
            repeat(10) { assertEquals(HttpStatusCode.Unauthorized, login("nobody", "guess$it")) }
            assertEquals(HttpStatusCode.TooManyRequests, login("someone", "guess"))
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }
}

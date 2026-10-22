package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.LoginLimiter
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.LoginUnlockRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginLimitsResponse
import org.mr0btain.ticketman.server.response.LoginLock
import org.mr0btain.ticketman.server.response.LoginLockKind
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Wrong password limits per username, per address and across the server */
class LoginLimiterTest {
    private var clock = 1_000_000L
    private val locks = mutableListOf<LoginLock>()
    private var surges = 0
    private val limiter = LoginLimiter(now = { clock }, onLock = { locks += it }, onSurge = { surges++ })

    private fun wrong(address: String, username: String, times: Int = 1) = repeat(times) { limiter.failed(address, username) }

    @Test
    fun aUsernameGetsFreeTriesThenWaitsLongerEachTime() {
        wrong("10.0.0.1", "tech", LoginLimiter.FREE_TRIES)
        assertNull(limiter.waitFor("10.0.0.1", "tech"))
        assertTrue(locks.isEmpty())

        wrong("10.0.0.1", "Tech")
        assertEquals(30.seconds.inWholeMilliseconds, limiter.waitFor("10.0.0.2", "tech"), "Any device, any case")
        assertEquals(LoginLock(LoginLockKind.USERNAME, "tech", 6, clock + 30_000), locks.single())

        clock += 30.seconds.inWholeMilliseconds
        assertNull(limiter.waitFor("10.0.0.1", "tech"))
        wrong("10.0.0.1", "tech")
        assertEquals(60.seconds.inWholeMilliseconds, limiter.waitFor("10.0.0.1", "tech"))

        assertEquals(15.minutes, LoginLimiter.waitAfter(40), "Never longer than the longest wait")
    }

    @Test
    fun aGoodLoginOrAQuietWindowStartsTheCountOver() {
        wrong("10.0.0.1", "tech", LoginLimiter.FREE_TRIES)
        limiter.succeeded("tech")
        wrong("10.0.0.1", "tech")
        assertNull(limiter.waitFor("10.0.0.1", "tech"))

        wrong("10.0.0.1", "sam", LoginLimiter.FREE_TRIES)
        clock += LoginLimiter.WINDOW.inWholeMilliseconds + 1
        wrong("10.0.0.1", "sam")
        assertNull(limiter.waitFor("10.0.0.1", "sam"))
    }

    @Test
    fun anAddressIsCappedAcrossUsernames() {
        (1..LoginLimiter.ADDRESS_MAX).forEach { wrong("10.0.0.1", "name$it") }

        val wait = assertNotNull(limiter.waitFor("10.0.0.1", "someone"))
        assertEquals(LoginLimiter.WINDOW.inWholeMilliseconds, wait)
        assertNull(limiter.waitFor("10.0.0.2", "someone"), "Other devices are fine")
        assertEquals(LoginLockKind.ADDRESS, locks.single().kind)

        // The oldest try ages out, there's room for one more
        clock += wait
        assertNull(limiter.waitFor("10.0.0.1", "someone"))
    }

    @Test
    fun guessingFromManyAddressesGivesEachOneTry() {
        // Never enough from any one address or on any one name to be locked
        (1 until LoginLimiter.SURGE_FAILURES).forEach { wrong("10.1.0.$it", "name$it") }
        assertNull(limiter.waitFor("10.1.0.1", "anyone"))
        assertEquals(0, surges)

        wrong("10.2.0.1", "another")
        assertEquals(1, surges)
        assertNotNull(limiter.waitFor("10.1.0.1", "anyone"), "Guessed wrong already")
        assertNull(limiter.waitFor("10.9.9.9", "tech"), "Never guessed wrong, so staff still get in")
        assertTrue(limiter.snapshot().surge)

        clock += LoginLimiter.WINDOW.inWholeMilliseconds + 1
        assertNull(limiter.waitFor("10.1.0.1", "anyone"))
        assertFalse(limiter.snapshot().surge)
    }

    @Test
    fun anAdminCanLiftALock() {
        wrong("10.0.0.1", "tech", LoginLimiter.FREE_TRIES + 1)
        assertEquals(listOf("tech"), limiter.snapshot().locks.map { it.key })

        assertTrue(limiter.unlock(LoginLockKind.USERNAME, " TECH "))
        assertNull(limiter.waitFor("10.0.0.1", "tech"))
        assertFalse(limiter.unlock(LoginLockKind.USERNAME, "tech"), "Nothing left to lift")
        assertFalse(limiter.unlock(LoginLockKind.ADDRESS, "10.9.9.9"))
    }

    private suspend fun ApplicationTestBuilder.login(username: String, password: String) = client.post("/api/auth") {
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(LoginRequest(username, password)))
    }

    @Test
    fun aLockedUsernameIsRefusedEvenWithTheRightPassword() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir)
            startApplication()
            UserDatabase().add(User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL))

            // Good logins never count
            repeat(15) { assertEquals(HttpStatusCode.Accepted, login("tech", "tech_pass").status) }

            repeat(LoginLimiter.FREE_TRIES + 1) { assertEquals(HttpStatusCode.Unauthorized, login("Tech", "guess$it").status) }

            val refused = login("tech", "tech_pass")
            assertEquals(HttpStatusCode.TooManyRequests, refused.status)
            assertEquals("30", refused.headers[HttpHeaders.RetryAfter])
            assertEquals("Too many wrong logins, try again in 30 seconds", refused.bodyAsText())
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    @Test
    fun adminsSeeAndLiftLocks() = testApplication {
        val dataDir = createTestDataDir()
        val json = Json { ignoreUnknownKeys = true }

        try {
            configureTestServer(dataDir)
            startApplication()
            UserDatabase().add(User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN))
            UserDatabase().add(User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL))
            val token = json.decodeFromString<AuthResponse>(login("boss", "boss_pass").bodyAsText()).accessToken!!

            repeat(LoginLimiter.FREE_TRIES + 1) { login("tech", "guess$it") }

            val limits = json.decodeFromString<LoginLimitsResponse>(client.get("/api/admin/logins") { bearerAuth(token) }.bodyAsText())
            val lock = limits.locks.single()
            assertEquals(LoginLockKind.USERNAME, lock.kind)
            assertEquals("tech", lock.key)
            assertEquals(LoginLimiter.FREE_TRIES + 1, lock.failures)
            assertEquals(LoginLimiter.FREE_TRIES + 1, limits.recentFailures)

            // Each wrong password is in the audit log already, the lock is too
            val audit = client.get("/api/admin/audit/fetch?offset=0&limit=50") { bearerAuth(token) }.bodyAsText()
            assertContains(audit, "Locked out for 30 seconds after 6 wrong passwords")

            val unlock = client.post("/api/admin/logins/unlock") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(LoginUnlockRequest(LoginLockKind.USERNAME, "tech")))
            }
            assertEquals(HttpStatusCode.Accepted, unlock.status)
            assertEquals(HttpStatusCode.Accepted, login("tech", "tech_pass").status)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }
}

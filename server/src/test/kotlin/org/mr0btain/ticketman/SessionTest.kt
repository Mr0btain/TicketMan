package org.mr0btain.ticketman

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.TokenTable
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*

/**
 * Sessions per device and user statuses
 */
class SessionTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(): UserDatabase {
        configureTestServer(dataDir)
        startApplication()

        return UserDatabase().apply {
            add(admin)
            add(tech)
        }
    }

    private suspend fun ApplicationTestBuilder.login(user: User, deviceId: String = "", deviceName: String = ""): AuthResponse {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password, deviceId, deviceName)))
        }

        assertEquals(HttpStatusCode.Accepted, response.status, "Login for ${user.username}")
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.me(token: String) =
        client.get("/api/user/me") { bearerAuth(token) }.status

    private suspend fun ApplicationTestBuilder.sessions(adminToken: String): List<SessionResponse> =
        json.decodeFromString(client.get("/api/admin/sessions") { bearerAuth(adminToken) }.bodyAsText())

    @Test
    fun eachDeviceGetsItsOwnSession() = testApplication {
        setup()

        val desk = login(tech, "desk-1", "Back Desk")
        val counter = login(tech, "counter-1", "Front Counter")

        // Logging in on the counter doesn't log the desk out
        assertEquals(HttpStatusCode.OK, me(desk.accessToken!!))
        assertEquals(HttpStatusCode.OK, me(counter.accessToken!!))

        val adminToken = login(admin).accessToken!!
        val techSessions = sessions(adminToken).filter { it.userIdentifier == tech.identifier }
        assertEquals(setOf("Back Desk", "Front Counter"), techSessions.map { it.deviceName }.toSet())
    }

    @Test
    fun sameDeviceLoggingInAgainReplacesItsSession() = testApplication {
        setup()

        val first = login(tech, "desk-1", "Back Desk")
        val second = login(tech, "desk-1", "Back Desk")

        assertEquals(HttpStatusCode.Unauthorized, me(first.accessToken!!))
        assertEquals(HttpStatusCode.OK, me(second.accessToken!!))
        assertEquals(1, TokenDatabase().getAll().count { it.userIdentifier == tech.identifier.toString() })
    }

    @Test
    fun logoutOnlyEndsThatDevice() = testApplication {
        setup()
        val desk = login(tech, "desk-1")
        val counter = login(tech, "counter-1")

        val response = client.post("/api/auth/logout") { bearerAuth(desk.accessToken!!) }
        assertEquals(HttpStatusCode.Accepted, response.status)

        assertEquals(HttpStatusCode.Unauthorized, me(desk.accessToken!!))
        assertEquals(HttpStatusCode.OK, me(counter.accessToken!!))
    }

    @Test
    fun refreshKeepsTheSameSession() = testApplication {
        setup()
        val desk = login(tech, "desk-1")

        val refreshed = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RefreshTokenRequest(desk.refreshToken!!)))
        }

        assertEquals(HttpStatusCode.Accepted, refreshed.status)
        val newAccess = json.decodeFromString<RefreshTokenResponse>(refreshed.bodyAsText()).token
        assertEquals(HttpStatusCode.OK, me(newAccess))
    }

    private suspend fun ApplicationTestBuilder.refresh(token: String) =
        client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RefreshTokenRequest(token)))
        }

    private fun sessionOf(token: String): String = JWT.decode(token).getClaim(JwtService.SESSION_CLAIM).asString()

    @Test
    fun refreshingSwapsTheRefreshToken() = testApplication {
        setup()
        val first = login(tech, "desk-1").refreshToken!!

        val response = refresh(first)
        assertEquals(HttpStatusCode.Accepted, response.status)
        val second = json.decodeFromString<RefreshTokenResponse>(response.bodyAsText()).refreshToken!!
        assertNotEquals(first, second)

        // The old one is no good now, so a copy of it stops working once the real device refreshes
        assertEquals(HttpStatusCode.Unauthorized, refresh(first).status)
        assertEquals(HttpStatusCode.Accepted, refresh(second).status)

        // Refreshing doesn't stretch the session, the new token runs out when the first one would have
        assertEquals(JWT.decode(first).expiresAt, JWT.decode(second).expiresAt)
        assertEquals(sessionOf(first), sessionOf(second))
    }

    @Test
    fun expiredSessionsStopWorkingAndAreSwept() = testApplication {
        setup()
        val desk = login(tech, "desk-1")
        val other = login(admin, "desk-2")
        val sessionId = sessionOf(desk.accessToken!!)
        val now = System.currentTimeMillis()

        // The expiry is kept from the refresh token
        val stored = TokenDatabase().findBySessionId(sessionId)!!
        assertEquals(JWT.decode(desk.refreshToken).expiresAt.time, stored.expiresAt)

        transaction {
            TokenTable.update({ TokenTable.sessionId eq sessionId }) { it[expiresAt] = now - 1 }
        }

        assertEquals(HttpStatusCode.Unauthorized, me(desk.accessToken!!))
        assertEquals(HttpStatusCode.Unauthorized, refresh(desk.refreshToken!!).status)

        assertEquals(1, TokenDatabase().removeExpired(now))
        assertNull(TokenDatabase().findBySessionId(sessionId))
        assertEquals(HttpStatusCode.OK, me(other.accessToken!!))
    }

    @Test
    fun sessionsFromBeforeExpiryWasKeptGetItFromTheirToken() = testApplication {
        setup()
        val desk = login(tech, "desk-1")
        val sessionId = sessionOf(desk.accessToken!!)

        transaction {
            TokenTable.update({ TokenTable.sessionId eq sessionId }) { it[expiresAt] = 0 }
        }
        TokenDatabase().backfillExpiry()

        assertEquals(JWT.decode(desk.refreshToken).expiresAt.time, TokenDatabase().findBySessionId(sessionId)!!.expiresAt)
    }

    @Test
    fun adminCanLogAUserOutEverywhere() = testApplication {
        setup()
        val desk = login(tech, "desk-1")
        val counter = login(tech, "counter-1")
        val adminToken = login(admin).accessToken!!

        val response = client.delete("/api/admin/sessions/user/${tech.identifier}") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.Accepted, response.status)

        assertEquals(HttpStatusCode.Unauthorized, me(desk.accessToken!!))
        assertEquals(HttpStatusCode.Unauthorized, me(counter.accessToken!!))
        assertEquals(HttpStatusCode.OK, me(adminToken))
    }

    @Test
    fun deactivatingThroughUpdateEndsSessionsAndBlocksLogin() = testApplication {
        setup()
        val desk = login(tech, "desk-1")
        val adminToken = login(admin).accessToken!!

        val response = client.post("/api/user/update") {
            contentType(ContentType.Application.Json)
            bearerAuth(adminToken)
            setBody(json.encodeToString(tech.copy(password = "", status = UserStatus.DEACTIVATED)))
        }
        assertEquals(HttpStatusCode.Accepted, response.status)

        assertEquals(HttpStatusCode.Unauthorized, me(desk.accessToken!!))
        val relogin = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(tech.username, tech.password)))
        }
        assertEquals(HttpStatusCode.Unauthorized, relogin.status)
    }

    @Test
    fun adminsCantChangeTheirOwnStatus() = testApplication {
        setup()
        val adminToken = login(admin).accessToken!!

        val response = client.post("/api/user/update") {
            contentType(ContentType.Application.Json)
            bearerAuth(adminToken)
            setBody(json.encodeToString(admin.copy(password = "", status = UserStatus.DEACTIVATED)))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun oldDeactivatedUsersAndSessionsAreMigrated() = testApplication {
        val users = setup()

        // How things looked before statuses and session ids
        transaction {
            UserTable.update(where = { UserTable.id eq tech.identifier }) { it[UserTable.access] = AccessLevel.NONE }
            TokenTable.insert { row ->
                row[TokenTable.identifier] = admin.identifier.toString()
                row[TokenTable.token] = "old-token"
            }
        }

        LocalDatabase(dataDir.resolve("server").invariantSeparatorsPathString).initDatabase()

        val migrated = users.getById(tech.identifier)!!
        assertEquals(UserStatus.DEACTIVATED, migrated.status)
        assertEquals(AccessLevel.RESTRICTED, migrated.accessLevel)
        assertNull(TokenDatabase().findByToken("old-token"))
    }
}

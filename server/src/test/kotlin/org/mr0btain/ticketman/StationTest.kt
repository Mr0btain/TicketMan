package org.mr0btain.ticketman

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.TokenTable
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PinLoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.request.SetPinRequest
import org.mr0btain.ticketman.server.request.StationRegistrationRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.SessionKind
import org.mr0btain.ticketman.server.response.StationRegistrationResponse
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Stations and PIN sign in
 */
@OptIn(ExperimentalUuidApi::class)
class StationTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", name = "Terry Tech", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", name = "Cleo Clerk", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var users: UserDatabase
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        users = UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
            setPin(tech.identifier, "246813")
            setPin(clerk.identifier, "135792")
        }
        adminToken = login(admin)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            postJson("/api/auth", json.encodeToString(LoginRequest(user.username, user.password, "own-${user.username}"))).bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            token?.let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.register(deviceId: String = "counter-1", approve: Boolean = true): StationCredentials {
        val response = postJson("/api/station/register", json.encodeToString(StationRegistrationRequest(deviceId, "Front Counter")), login(tech))
        assertEquals(HttpStatusCode.Created, response.status)

        val registration = json.decodeFromString<StationRegistrationResponse>(response.bodyAsText())
        assertEquals(StationStatus.PENDING, registration.station.status)

        if (approve) {
            val approved = client.post("/api/admin/stations/${registration.station.identifier}/approve") { bearerAuth(adminToken) }
            assertEquals(HttpStatusCode.Accepted, approved.status)
        }

        return registration.credentials
    }

    private suspend fun ApplicationTestBuilder.pin(station: StationCredentials, user: User, pin: String) =
        postJson("/api/auth/pin", json.encodeToString(PinLoginRequest(station, user.identifier, pin)))

    @Test
    fun pinRules() {
        assertNull(PinRules.problem("246813"))
        assertNotNull(PinRules.problem("12345"))
        assertNotNull(PinRules.problem("12345a"))
        assertNotNull(PinRules.problem("111111"))
        assertNotNull(PinRules.problem("123456"))
        assertNotNull(PinRules.problem("987654"))
    }

    @Test
    fun pinWorksAtAnApprovedStation() = testApplication {
        setup()
        val station = register()

        val response = pin(station, tech, "246813")
        assertEquals(HttpStatusCode.Accepted, response.status)

        val token = json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
        val me = json.decodeFromString<User>(client.get("/api/user/me") { bearerAuth(token) }.bodyAsText())
        assertEquals(tech.identifier, me.identifier)

        val session = TokenDatabase().getAll().single { it.kind == SessionKind.PIN }
        assertEquals("counter-1", session.deviceId)
        assertEquals("Front Counter", session.deviceName)
    }

    @Test
    fun pinDoesntWorkBeforeApprovalOrWithTheWrongSecret() = testApplication {
        setup()
        val pending = register(approve = false)

        assertEquals(HttpStatusCode.Forbidden, pin(pending, tech, "246813").status)
        assertEquals(HttpStatusCode.Forbidden, pin(pending.copy(secret = "guessed"), tech, "246813").status)
        assertEquals(HttpStatusCode.Forbidden, pin(StationCredentials(Uuid.random(), "x"), tech, "246813").status)
    }

    @Test
    fun switchingUsersEndsThePreviousPinSession() = testApplication {
        setup()
        val station = register()
        val techToken = json.decodeFromString<AuthResponse>(pin(station, tech, "246813").bodyAsText()).accessToken!!

        val clerkToken = json.decodeFromString<AuthResponse>(pin(station, clerk, "135792").bodyAsText()).accessToken!!

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/user/me") { bearerAuth(techToken) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/user/me") { bearerAuth(clerkToken) }.status)

        // Their own password session on another device is left alone
        val techOwn = login(tech)
        pin(station, clerk, "135792")
        assertEquals(HttpStatusCode.OK, client.get("/api/user/me") { bearerAuth(techOwn) }.status)
    }

    @Test
    fun pinLocksAfterTooManyWrongTries() = testApplication {
        setup()
        val station = register()

        repeat(PinRules.MAX_ATTEMPTS - 1) {
            val wrong = pin(station, tech, "000001")
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
            assertEquals("Wrong PIN, ${PinRules.MAX_ATTEMPTS - 1 - it} tries left", wrong.bodyAsText())
        }

        assertEquals(HttpStatusCode.Locked, pin(station, tech, "000001").status)

        // Even the right PIN is refused once locked
        assertEquals(HttpStatusCode.Locked, pin(station, tech, "246813").status)
        assertTrue(users.getById(tech.identifier)!!.pinLocked)

        // Locked users drop off the station's list
        val staff = json.decodeFromString<List<StaffMember>>(postJson("/api/auth/station/staff", json.encodeToString(station)).bodyAsText())
        assertEquals(listOf(clerk.identifier), staff.map { it.identifier })

        assertEquals(HttpStatusCode.Accepted, client.post("/api/user/pin/${tech.identifier}/unlock") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.Accepted, pin(station, tech, "246813").status)
    }

    @Test
    fun rightPinResetsTheWrongCount() = testApplication {
        setup()
        val station = register()

        repeat(PinRules.MAX_ATTEMPTS - 1) { pin(station, tech, "000001") }
        assertEquals(HttpStatusCode.Accepted, pin(station, tech, "246813").status)
        repeat(PinRules.MAX_ATTEMPTS - 1) { pin(station, tech, "000001") }

        assertFalse(users.getById(tech.identifier)!!.pinLocked)
    }

    @Test
    fun staffListOnlyShowsPeopleWhoCanPinIn() = testApplication {
        setup()
        val station = register()
        val noPin = User(username = "nopin", password = "nopin_pass", accessLevel = AccessLevel.NORMAL).also { users.add(it) }

        val staff = json.decodeFromString<List<StaffMember>>(postJson("/api/auth/station/staff", json.encodeToString(station)).bodyAsText())

        assertEquals(setOf(tech.identifier, clerk.identifier), staff.map { it.identifier }.toSet())
        assertFalse(noPin.identifier in staff.map { it.identifier })

        val status = json.decodeFromString<Station>(postJson("/api/auth/station/status", json.encodeToString(station)).bodyAsText())
        assertEquals(StationStatus.APPROVED, status.status)
    }

    @Test
    fun usersChangeTheirOwnPinWithTheirPassword() = testApplication {
        setup()
        val station = register()
        val token = login(tech)

        assertEquals(HttpStatusCode.BadRequest, postJson("/api/user/me/pin", json.encodeToString(SetPinRequest("wrong", "975310")), token).status)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/user/me/pin", json.encodeToString(SetPinRequest(tech.password, "123456")), token).status)
        assertEquals(HttpStatusCode.Accepted, postJson("/api/user/me/pin", json.encodeToString(SetPinRequest(tech.password, "975310")), token).status)

        assertEquals(HttpStatusCode.Unauthorized, pin(station, tech, "246813").status)
        assertEquals(HttpStatusCode.Accepted, pin(station, tech, "975310").status)
    }

    @Test
    fun adminResetClearsThePin() = testApplication {
        setup()
        val station = register()

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/user/pin/${tech.identifier}") { bearerAuth(adminToken) }.status)

        assertFalse(users.getById(tech.identifier)!!.hasPin)
        assertEquals(HttpStatusCode.Unauthorized, pin(station, tech, "246813").status)
    }

    @Test
    fun removingAStationSignsOutItsPinSession() = testApplication {
        setup()
        val station = register()
        val token = json.decodeFromString<AuthResponse>(pin(station, tech, "246813").bodyAsText()).accessToken!!

        val listed = json.decodeFromString<List<Station>>(client.get("/api/admin/stations") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(listOf(station.stationId), listed.map { it.identifier })

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/admin/stations/${station.stationId}") { bearerAuth(adminToken) }.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/user/me") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Forbidden, pin(station, tech, "246813").status)
    }

    @Test
    fun restrictedUsersCantRegisterStations() = testApplication {
        setup()

        val response = postJson("/api/station/register", json.encodeToString(StationRegistrationRequest("x", "Mine")), login(clerk))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun sessionOf(token: String): String = JWT.decode(token).getClaim(JwtService.SESSION_CLAIM).asString()

    // Makes a session look like nobody has used it for a while
    private fun leaveUnused(token: String, seconds: Long) {
        val sessionId = sessionOf(token)
        transaction {
            TokenTable.update({ TokenTable.sessionId eq sessionId }) { it[lastUsedAt] = System.currentTimeMillis() - seconds * 1_000 }
        }
    }

    private suspend fun ApplicationTestBuilder.kioskStation(idleSeconds: Int = 90): StationCredentials {
        val station = register()
        val on = postJson("/api/admin/stations/${station.stationId}/kiosk", json.encodeToString(KioskSettings(true, idleTimeoutSeconds = idleSeconds)), adminToken)
        assertEquals(HttpStatusCode.Accepted, on.status)
        return station
    }

    @Test
    fun aKioskPinSessionEndsWhenLeftAlone() = testApplication {
        setup()
        val station = kioskStation(idleSeconds = 90)
        val tokens = json.decodeFromString<AuthResponse>(pin(station, tech, "246813").bodyAsText())
        val access = tokens.accessToken!!

        val stored = TokenDatabase().findBySessionId(sessionOf(access))!!
        assertEquals(90 + UserService.KIOSK_IDLE_MARGIN.inWholeSeconds.toInt(), stored.idleTimeoutSeconds)

        // Being used keeps it going
        leaveUnused(access, 60)
        assertEquals(HttpStatusCode.OK, client.get("/api/user/me") { bearerAuth(access) }.status)

        // Switched off with someone signed in: the server ends it a while after the kiosk would have locked
        leaveUnused(access, stored.idleTimeoutSeconds + 1L)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/user/me") { bearerAuth(access) }.status)
        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/auth/refresh", json.encodeToString(RefreshTokenRequest(tokens.refreshToken!!))).status)

        assertEquals(1, TokenDatabase().removeExpired())
    }

    @Test
    fun pinSessionsAwayFromAKioskHaveNoIdleLimit() = testApplication {
        setup()
        val station = register()
        val token = json.decodeFromString<AuthResponse>(pin(station, tech, "246813").bodyAsText()).accessToken!!

        assertEquals(0, TokenDatabase().findBySessionId(sessionOf(token))!!.idleTimeoutSeconds)
    }

    @Test
    fun anAdminLeavingAKioskKeepsTheirSession() = testApplication {
        setup()
        users.setPin(admin.identifier, "975310")
        val station = kioskStation()

        // Only admins leave kiosk mode, and only from a PIN session
        val techPin = json.decodeFromString<AuthResponse>(pin(station, tech, "246813").bodyAsText()).accessToken!!
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/auth/kiosk/leave") { bearerAuth(techPin) }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/auth/kiosk/leave") { bearerAuth(adminToken) }.status)

        val adminPin = json.decodeFromString<AuthResponse>(pin(station, admin, "975310").bodyAsText()).accessToken!!
        assertEquals(HttpStatusCode.Accepted, client.post("/api/auth/kiosk/leave") { bearerAuth(adminPin) }.status)
        assertEquals(0, TokenDatabase().findBySessionId(sessionOf(adminPin))!!.idleTimeoutSeconds)

        leaveUnused(adminPin, 3_600)
        assertEquals(HttpStatusCode.OK, client.get("/api/user/me") { bearerAuth(adminPin) }.status)
    }

    @Test
    fun adminsTurnKioskModeOnForApprovedStations() = testApplication {
        setup()
        val pending = register(deviceId = "pending-1", approve = false)

        val refused = postJson("/api/admin/stations/${pending.stationId}/kiosk", json.encodeToString(KioskSettings(true)), adminToken)
        assertEquals(HttpStatusCode.Conflict, refused.status)

        val station = register()
        val tooShort = postJson("/api/admin/stations/${station.stationId}/kiosk", json.encodeToString(KioskSettings(true, idleTimeoutSeconds = 5)), adminToken)
        assertEquals(HttpStatusCode.BadRequest, tooShort.status)

        val on = postJson("/api/admin/stations/${station.stationId}/kiosk", json.encodeToString(KioskSettings(true, idleTimeoutSeconds = 90)), adminToken)
        assertEquals(HttpStatusCode.Accepted, on.status)

        // The station sees it with its own credentials
        val status = json.decodeFromString<Station>(postJson("/api/auth/station/status", json.encodeToString(station)).bodyAsText())
        assertTrue(status.kioskEnabled)
        assertEquals(90, status.idleTimeoutSeconds)

        // Only admins
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/admin/stations/${station.stationId}/kiosk", json.encodeToString(KioskSettings(false)), login(tech)).status)
    }
}

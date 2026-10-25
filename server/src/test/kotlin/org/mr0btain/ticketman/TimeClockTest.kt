package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.TimeClockDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.data.timeclock.ClockState
import org.mr0btain.ticketman.data.timeclock.PunchResult
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.timeclock.Timesheet
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PinLoginRequest
import org.mr0btain.ticketman.server.request.StationRegistrationRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.StationRegistrationResponse
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Clocking in and out by PIN at a station or from your own device, timesheets, and admins fixing times
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class TimeClockTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", name = "Tess Tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", name = "Cal Clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)
    private val today = LocalDate.now()
    private val hour = 3_600_000L

    private lateinit var adminToken: String
    private lateinit var clerkToken: String
    private lateinit var station: StationCredentials

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
            setPin(tech.identifier, "246813")
            setPin(clerk.identifier, "135792")
        }
        adminToken = login(admin)
        clerkToken = login(clerk)

        val registration = json.decodeFromString<StationRegistrationResponse>(
            post("/api/station/register", json.encodeToString(StationRegistrationRequest("counter-1", "Front Counter")), login(tech)).bodyAsText()
        )
        client.post("/api/admin/stations/${registration.station.identifier}/approve") { bearerAuth(adminToken) }
        station = registration.credentials
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(post("/api/auth", json.encodeToString(LoginRequest(user.username, user.password, "own-${user.username}"))).bodyAsText()).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            token?.let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.punch(user: User, pin: String) =
        post("/api/timeclock/punch", json.encodeToString(PinLoginRequest(station, user.identifier, pin)))

    private suspend fun ApplicationTestBuilder.timesheet(token: String = adminToken, path: String = "/api/timeclock/timesheet"): Timesheet {
        val response = client.get("$path?from=$today&to=$today") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private fun at(hourOfDay: Int, day: LocalDate = today) = day.atTime(hourOfDay, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun aPinAtAStationClocksInThenOut() = testApplication {
        setup()

        val first = punch(tech, "246813")
        assertEquals(HttpStatusCode.OK, first.status)
        val clockedIn = json.decodeFromString<PunchResult>(first.bodyAsText())
        assertTrue(clockedIn.clockedIn)
        assertEquals("Front Counter", clockedIn.entry.station)
        assertEquals("Tess Tech", clockedIn.entry.userName)

        // Anyone logged in sees who's on
        val now = json.decodeFromString<List<TimeEntry>>(client.get("/api/timeclock/now") { bearerAuth(clerkToken) }.bodyAsText())
        assertEquals(listOf(tech.identifier), now.map { it.userIdentifier })

        val second = json.decodeFromString<PunchResult>(punch(tech, "246813").bodyAsText())
        assertFalse(second.clockedIn)
        assertEquals(clockedIn.entry.identifier, second.entry.identifier)
        assertNotNull(second.entry.clockedOutAt)

        val sheet = timesheet()
        assertEquals(1, sheet.people.single().entries.size)
    }

    @Test
    fun aWrongPinOrStationIsTurnedAway() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, punch(tech, "000000").status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/timeclock/punch", json.encodeToString(PinLoginRequest(StationCredentials(Uuid.random(), "x"), tech.identifier, "246813"))).status)
        assertNull(TimeClockDatabase().open(tech.identifier))
    }

    @Test
    fun clockingFromYourOwnDeviceAndYourOwnTimesheet() = testApplication {
        setup()
        assertNull(json.decodeFromString<ClockState>(client.get("/api/timeclock/me") { bearerAuth(clerkToken) }.bodyAsText()).entry)

        val punched = client.post("/api/timeclock/me/punch") { bearerAuth(clerkToken) }
        assertEquals(HttpStatusCode.OK, punched.status)
        assertEquals("", json.decodeFromString<PunchResult>(punched.bodyAsText()).entry.station)
        assertNotNull(json.decodeFromString<ClockState>(client.get("/api/timeclock/me") { bearerAuth(clerkToken) }.bodyAsText()).entry)

        // The tech's time isn't on the clerk's own sheet, and the full one is admin only
        punch(tech, "246813")
        assertEquals(listOf(clerk.identifier), timesheet(clerkToken, "/api/timeclock/me/timesheet").people.map { it.userIdentifier })
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/timeclock/timesheet?from=$today&to=$today") { bearerAuth(clerkToken) }.status)
        assertEquals(2, timesheet().people.size)
    }

    @Test
    fun adminsAddFixAndDeleteTime() = testApplication {
        setup()
        val yesterday = today.minusDays(1)
        val forgotten = TimeEntry(userIdentifier = tech.identifier, clockedInAt = at(9, yesterday), clockedOutAt = at(17, yesterday), note = "Forgot to clock")

        val added = post("/api/timeclock/entry/add", json.encodeToString(forgotten), adminToken)
        assertEquals(HttpStatusCode.Created, added.status, added.bodyAsText())
        val saved = json.decodeFromString<TimeEntry>(added.bodyAsText())
        assertEquals("boss", saved.editedBy)
        assertEquals("Tess Tech", saved.userName)

        // Overlapping, backwards and future times are refused
        assertEquals(HttpStatusCode.BadRequest, post("/api/timeclock/entry/add", json.encodeToString(forgotten.copy(identifier = Uuid.generateV7(), clockedInAt = at(16, yesterday), clockedOutAt = at(18, yesterday))), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/timeclock/entry/add", json.encodeToString(forgotten.copy(identifier = Uuid.generateV7(), clockedInAt = at(20, yesterday), clockedOutAt = at(19, yesterday))), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/timeclock/entry/add", json.encodeToString(forgotten.copy(identifier = Uuid.generateV7(), clockedInAt = System.currentTimeMillis() + hour, clockedOutAt = null)), adminToken).status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/timeclock/entry/add", json.encodeToString(forgotten), clerkToken).status)

        val fixed = post("/api/timeclock/entry/update", json.encodeToString(saved.copy(clockedOutAt = at(13, yesterday), userIdentifier = clerk.identifier)), adminToken)
        assertEquals(HttpStatusCode.OK, fixed.status)
        val changed = json.decodeFromString<TimeEntry>(fixed.bodyAsText())
        assertEquals(4.0, changed.hours(0))
        assertEquals(tech.identifier, changed.userIdentifier, "whose time it is stays")

        val week = client.get("/api/timeclock/timesheet?from=${yesterday}&to=$today&user=${tech.identifier}") { bearerAuth(adminToken) }
        assertEquals(4.0, json.decodeFromString<Timesheet>(week.bodyAsText()).people.single().hours)

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/timeclock/entry/remove/${saved.identifier}") { bearerAuth(adminToken) }.status)
        assertNull(TimeClockDatabase().get(saved.identifier))
    }
}

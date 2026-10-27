package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.schedule.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.LOCATION_HEADER
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The staff schedule: admins write shifts and time off, everyone reads them, weekly repeats as a series
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class ScheduleTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val zone = TimeZone.currentSystemDefault()

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", name = "Nina Tech", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var adminToken: String
    private lateinit var techToken: String
    private lateinit var clerkToken: String

    // A Monday a little ahead, so every day of the tests is in the future
    private val monday: LocalDate = Clock.System.now().toLocalDateTime(zone).date.let { today ->
        today.plus(DatePeriod(days = 7)).let { it.minus(DatePeriod(days = it.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal)) }
    }

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
        adminToken = login(admin)
        techToken = login(tech)
        clerkToken = login(clerk)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()

    private fun shift(day: LocalDate, from: Int = 9, to: Int = 17) = ScheduleEntry(userIdentifier = tech.identifier, startsAt = at(day, from), endsAt = at(day, to))

    private suspend fun ApplicationTestBuilder.add(request: ScheduleRequest, token: String = adminToken, location: Location? = null) =
        client.post("/api/schedule/add") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            location?.let { header(LOCATION_HEADER, it.identifier.toString()) }
            setBody(json.encodeToString(request))
        }

    private suspend fun ApplicationTestBuilder.range(from: LocalDate, to: LocalDate, token: String = clerkToken, location: Location? = null): List<ScheduleEntry> {
        val response = client.get("/api/schedule/range?from=$from&to=$to") {
            bearerAuth(token)
            location?.let { header(LOCATION_HEADER, it.identifier.toString()) }
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun adminsWriteAndEveryoneReads() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Forbidden, add(ScheduleRequest(shift(monday)), techToken).status)

        val response = add(ScheduleRequest(shift(monday, 9, 17).copy(note = "  Opening  ")))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val saved = json.decodeFromString<List<ScheduleEntry>>(response.bodyAsText()).single()
        assertEquals("Nina Tech", saved.userName)
        assertEquals("Opening", saved.note)
        assertEquals("boss", saved.createdBy)
        assertEquals(8.0, saved.hours)
        assertNotNull(saved.locationIdentifier, "where the admin works")

        // The clerk sees it on its day, and not the week after
        assertEquals(listOf(saved.identifier), range(monday, monday).map { it.identifier })
        assertTrue(range(monday.plus(DatePeriod(days = 7)), monday.plus(DatePeriod(days = 13))).isEmpty())
    }

    @Test
    fun entriesAreChecked() = testApplication {
        setup()

        assertEquals(HttpStatusCode.BadRequest, add(ScheduleRequest(shift(monday, 17, 9))).status)
        assertEquals(HttpStatusCode.BadRequest, add(ScheduleRequest(shift(monday).copy(userIdentifier = kotlin.uuid.Uuid.random()))).status)
        assertEquals(HttpStatusCode.BadRequest, add(ScheduleRequest(shift(monday), ScheduleRepeat(emptyList(), monday.plus(DatePeriod(days = 14)).toString()))).status)

        UserDatabase().setState(tech.identifier, UserStatus.DEACTIVATED, needsSetup = false)
        assertEquals(HttpStatusCode.BadRequest, add(ScheduleRequest(shift(monday))).status)

        assertEquals(HttpStatusCode.BadRequest, client.get("/api/schedule/range?from=$monday&to=${monday.plus(DatePeriod(days = 200))}") { bearerAuth(clerkToken) }.status)
    }

    @Test
    fun timeOffIsWholeDays() = testApplication {
        setup()

        // Sent with times, saved midnight to midnight, Wednesday through Friday
        val wednesday = monday.plus(DatePeriod(days = 2))
        val friday = monday.plus(DatePeriod(days = 4))
        val off = ScheduleEntry(userIdentifier = tech.identifier, kind = ScheduleKind.TIME_OFF, startsAt = at(wednesday, 10), endsAt = at(friday, 15))
        val saved = json.decodeFromString<List<ScheduleEntry>>(add(ScheduleRequest(off)).bodyAsText()).single()

        assertTrue(saved.allDay)
        assertEquals(at(wednesday, 0), saved.startsAt)
        assertEquals(at(friday.plus(DatePeriod(days = 1)), 0), saved.endsAt)
        assertEquals(listOf(wednesday, monday.plus(DatePeriod(days = 3)), friday), saved.days(zone))
        assertEquals(0.0, saved.hours)

        // Seen on its middle day, and it can't repeat
        assertEquals(1, range(monday.plus(DatePeriod(days = 3)), monday.plus(DatePeriod(days = 3))).size)
        assertEquals(HttpStatusCode.BadRequest, add(ScheduleRequest(off.copy(identifier = kotlin.uuid.Uuid.generateV7()), ScheduleRepeat(listOf(3), friday.toString()))).status)
    }

    @Test
    fun aWeeklyRepeatIsASeries() = testApplication {
        setup()

        // Monday, Wednesday and Friday for three weeks
        val until = monday.plus(DatePeriod(days = 20))
        val response = add(ScheduleRequest(shift(monday), ScheduleRepeat(listOf(1, 3, 5), until.toString())))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val made = json.decodeFromString<List<ScheduleEntry>>(response.bodyAsText())
        assertEquals(9, made.size)
        assertEquals(setOf(made.first().identifier), made.map { it.seriesIdentifier }.toSet())
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), made.map { Instant.fromEpochMilliseconds(it.startsAt).toLocalDateTime(zone).dayOfWeek }.toSet())
        assertTrue(made.all { it.hours == 8.0 })

        // From the second week on, an hour later and longer: the first week stays as it was
        val second = made[3]
        val changed = second.copy(startsAt = second.startsAt + 3_600_000, endsAt = second.endsAt + 7_200_000, note = "Late opening")
        val updated = client.post("/api/schedule/update?scope=FOLLOWING") {
            contentType(ContentType.Application.Json)
            bearerAuth(adminToken)
            setBody(json.encodeToString(changed))
        }
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())
        assertEquals(6, json.decodeFromString<List<ScheduleEntry>>(updated.bodyAsText()).size)

        val after = range(monday, until)
        assertTrue(after.take(3).all { Instant.fromEpochMilliseconds(it.startsAt).toLocalDateTime(zone).hour == 9 && it.note.isEmpty() })
        assertTrue(after.drop(3).all { Instant.fromEpochMilliseconds(it.startsAt).toLocalDateTime(zone).hour == 10 && it.hours == 9.0 && it.note == "Late opening" })

        // Just one comes off, then the rest of the series
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/schedule/remove/${made[0].identifier}") { bearerAuth(adminToken) }.status)
        assertEquals(8, range(monday, until).size)
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/schedule/remove/${made[1].identifier}?scope=SERIES") { bearerAuth(adminToken) }.status)
        assertTrue(range(monday, until).isEmpty())
    }

    @Test
    fun eachLocationSeesItsOwn() = testApplication {
        setup()
        val downtown = Location(code = "DT", name = "Downtown")
        client.post("/api/location/add") {
            contentType(ContentType.Application.Json)
            bearerAuth(adminToken)
            setBody(json.encodeToString(downtown))
        }

        add(ScheduleRequest(shift(monday)))
        add(ScheduleRequest(shift(monday.plus(DatePeriod(days = 1)))), location = downtown)

        assertEquals(1, range(monday, monday.plus(DatePeriod(days = 6))).size, "the clerk works at the main location")
        assertEquals(1, range(monday, monday.plus(DatePeriod(days = 6)), location = downtown).size)
    }
}

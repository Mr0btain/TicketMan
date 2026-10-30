package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.schedule.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.time.Clock

/**
 * Time off: staff ask against a yearly allowance an admin sets, admins approve onto the schedule or decline
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class TimeOffTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val zone = TimeZone.currentSystemDefault()

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", name = "Nina Tech", accessLevel = AccessLevel.NORMAL)

    private lateinit var adminToken: String
    private lateinit var techToken: String

    // Days early in next year, so they're always ahead and in one year
    private val year = Clock.System.now().toLocalDateTime(zone).year + 1
    private val first = LocalDate(year, 2, 2)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
        adminToken = login(admin)
        techToken = login(tech)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private fun days(from: LocalDate, count: Int, hours: Double) = TimeOffRequest(
        startsAt = from.atStartOfDayIn(zone).toEpochMilliseconds(),
        endsAt = from.plus(DatePeriod(days = count)).atStartOfDayIn(zone).toEpochMilliseconds(),
        hours = hours,
        note = "Vacation"
    )

    private suspend fun ApplicationTestBuilder.mine(): MyTimeOff =
        json.decodeFromString(client.get("/api/timeoff/me?year=$year") { bearerAuth(techToken) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.allowance(hours: Double) =
        post("/api/timeoff/allowance/${tech.identifier}", json.encodeToString(TimeOffAllowance(hours)), adminToken)

    @Test
    fun requestsAreHeldToWhatsLeft() = testApplication {
        setup()

        // No allowance yet
        assertEquals(HttpStatusCode.Conflict, post("/api/timeoff/request", json.encodeToString(days(first, 1, 8.0)), techToken).status)

        assertEquals(HttpStatusCode.Forbidden, post("/api/timeoff/allowance/${tech.identifier}", json.encodeToString(TimeOffAllowance(80.0)), techToken).status)
        assertEquals(HttpStatusCode.BadRequest, allowance(-1.0).status)
        assertEquals(HttpStatusCode.OK, allowance(80.0).status)

        // 40 of 80 asked for, then 48 more is over what's left
        val asked = post("/api/timeoff/request", json.encodeToString(days(first, 5, 40.0)), techToken)
        assertEquals(HttpStatusCode.Created, asked.status, asked.bodyAsText())
        val request = json.decodeFromString<TimeOffRequest>(asked.bodyAsText())
        assertEquals(TimeOffStatus.PENDING, request.status)
        assertEquals(tech.identifier, request.userIdentifier)

        val tooMuch = post("/api/timeoff/request", json.encodeToString(days(first.plus(DatePeriod(days = 14)), 6, 48.0)), techToken)
        assertEquals(HttpStatusCode.Conflict, tooMuch.status)
        assertContains(tooMuch.bodyAsText(), "Only 40h left")

        val balance = mine().balance
        assertEquals(80.0, balance.allowanceHours)
        assertEquals(40.0, balance.pendingHours)
        assertEquals(40.0, balance.leftHours)

        // More hours than the days have
        assertEquals(HttpStatusCode.BadRequest, post("/api/timeoff/request", json.encodeToString(days(first, 1, 30.0)), techToken).status)
    }

    @Test
    fun approvingPutsItOnTheSchedule() = testApplication {
        setup()
        allowance(80.0)
        val request = json.decodeFromString<TimeOffRequest>(post("/api/timeoff/request", json.encodeToString(days(first, 3, 24.0)), techToken).bodyAsText())

        // Only admins see and answer the waiting list
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/timeoff/requests?status=PENDING") { bearerAuth(techToken) }.status)
        val waiting = json.decodeFromString<List<TimeOffRequest>>(client.get("/api/timeoff/requests?status=PENDING") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(listOf(request.identifier), waiting.map { it.identifier })
        assertEquals("Nina Tech", waiting.single().userName)

        val approved = post("/api/timeoff/approve/${request.identifier}", "{}", adminToken)
        assertEquals(HttpStatusCode.OK, approved.status, approved.bodyAsText())
        val answered = json.decodeFromString<TimeOffRequest>(approved.bodyAsText())
        assertEquals(TimeOffStatus.APPROVED, answered.status)
        assertEquals("boss", answered.decidedBy)

        // On the schedule as time off taking its hours
        val entries = json.decodeFromString<List<ScheduleEntry>>(client.get("/api/schedule/range?from=$first&to=${first.plus(DatePeriod(days = 2))}") { bearerAuth(techToken) }.bodyAsText())
        val entry = entries.single()
        assertEquals(ScheduleKind.TIME_OFF, entry.kind)
        assertEquals(24.0, entry.timeOffHours)
        assertEquals(request.identifier, entry.requestIdentifier)
        assertEquals(answered.entryIdentifier, entry.identifier)

        val balance = mine().balance
        assertEquals(24.0, balance.takenHours)
        assertEquals(0.0, balance.pendingHours)
        assertEquals(56.0, balance.leftHours)

        // Answered once only
        assertEquals(HttpStatusCode.Conflict, post("/api/timeoff/decline/${request.identifier}", json.encodeToString(TimeOffDecision("Busy")), adminToken).status)

        val balances = json.decodeFromString<List<TimeOffBalance>>(client.get("/api/timeoff/balances?year=$year") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(56.0, balances.single { it.userIdentifier == tech.identifier }.leftHours)
    }

    @Test
    fun declinedAndWithdrawnFreeTheirHours() = testApplication {
        setup()
        allowance(16.0)
        val one = json.decodeFromString<TimeOffRequest>(post("/api/timeoff/request", json.encodeToString(days(first, 1, 8.0)), techToken).bodyAsText())
        val two = json.decodeFromString<TimeOffRequest>(post("/api/timeoff/request", json.encodeToString(days(first.plus(DatePeriod(days = 7)), 1, 8.0)), techToken).bodyAsText())
        assertEquals(0.0, mine().balance.leftHours)

        val declined = post("/api/timeoff/decline/${one.identifier}", json.encodeToString(TimeOffDecision("  Short staffed  ")), adminToken)
        assertEquals("Short staffed", json.decodeFromString<TimeOffRequest>(declined.bodyAsText()).reason)
        assertEquals(HttpStatusCode.OK, post("/api/timeoff/withdraw/${two.identifier}", "{}", techToken).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/timeoff/withdraw/${two.identifier}", "{}", techToken).status)

        val mine = mine()
        assertEquals(16.0, mine.balance.leftHours)
        assertEquals(setOf(TimeOffStatus.DECLINED, TimeOffStatus.WITHDRAWN), mine.requests.map { it.status }.toSet())

        // Someone else's isn't theirs to withdraw
        assertEquals(HttpStatusCode.NotFound, post("/api/timeoff/withdraw/${one.identifier}", "{}", adminToken).status)
    }

    @Test
    fun defaultHoursCountWeekdays() {
        // A Friday to the Monday after: two weekdays
        val friday = LocalDate(2026, 10, 30)
        assertEquals(16.0, TimeOffRules.defaultHours(friday, friday.plus(DatePeriod(days = 3))))
    }

    @Test
    fun unusedHoursCarryOverUpToTheCapAndFloatingHolidaysAddToTheYear() = testApplication {
        setup()
        post("/api/timeoff/allowance/${tech.identifier}", json.encodeToString(TimeOffAllowance(80.0, carryOverMaxHours = 24.0, floatingHours = 8.0)), adminToken)

        // Only 16 used last year: 8 of it floating, so 72 of the allowance is unused and the cap keeps 24
        val lastYear = LocalDate(year - 1, 3, 2)
        val asked = json.decodeFromString<TimeOffRequest>(post("/api/timeoff/request", json.encodeToString(days(lastYear, 2, 16.0)), techToken).bodyAsText())
        assertEquals(HttpStatusCode.OK, post("/api/timeoff/approve/${asked.identifier}", "{}", adminToken).status)

        val balance = mine().balance
        assertEquals(80.0, balance.allowanceHours)
        assertEquals(24.0, balance.carriedOverHours)
        assertEquals(8.0, balance.floatingHours)
        assertEquals(112.0, balance.totalHours)
        assertEquals(112.0, balance.leftHours)

        // A request can use all of it, and no more
        assertEquals(HttpStatusCode.Conflict, post("/api/timeoff/request", json.encodeToString(days(first, 15, 113.0)), techToken).status)
        assertEquals(HttpStatusCode.Created, post("/api/timeoff/request", json.encodeToString(days(first, 15, 112.0)), techToken).status)

        // Setting just the hours, like an older client does, leaves the rest
        allowance(40.0)
        assertEquals(8.0, mine().balance.floatingHours)
        assertEquals(24.0, mine().balance.carryOverMaxHours)
    }

    @Test
    fun carryOverAndFloatingHoursAreChecked() = testApplication {
        setup()

        assertEquals(HttpStatusCode.BadRequest, post("/api/timeoff/allowance/${tech.identifier}", json.encodeToString(TimeOffAllowance(80.0, carryOverMaxHours = -1.0)), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/timeoff/allowance/${tech.identifier}", json.encodeToString(TimeOffAllowance(80.0, floatingHours = 5000.0)), adminToken).status)
    }
}

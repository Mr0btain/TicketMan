package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.timeclock.PunchResult
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.timeclock.Timesheet
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.data.report.SalesSummary
import org.mr0btain.ticketman.error.TmResult
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Reports, appointments and the time clock: client calls hit the routes the server has
 */
class BusinessRemoteRepositoryTest {
    private val requests = mutableListOf<String>()
    private val bodies = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val booked = Appointment(startsAt = 1_793_000_000_000, customerName = "Walk In Wendy")

    private val shift = TimeEntry(userIdentifier = Uuid.random(), clockedInAt = 1_793_000_000_000)

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}" + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        (request.body as? io.ktor.http.content.TextContent)?.let { bodies += it.text }

        when (request.url.encodedPath) {
            "/api/report/period" -> respond(Json.encodeToString(PeriodReport("2026-10-01", "2026-10-31", SalesSummary(invoices = 2))), HttpStatusCode.OK, jsonHeaders)
            "/api/report/commissions" ->
                if (request.method == HttpMethod.Post) respond("Commission has to be from 0% to 100%", HttpStatusCode.BadRequest)
                else respond("[]", HttpStatusCode.OK, jsonHeaders)
            "/api/appointment/range" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            "/api/appointment/add", "/api/appointment/update" -> respond(Json.encodeToString(booked), HttpStatusCode.OK, jsonHeaders)
            "/api/appointment/status/${booked.identifier}" -> respond("It was checked in already", HttpStatusCode.Conflict)
            "/api/timeclock/me" -> respond("""{"entry":null}""", HttpStatusCode.OK, jsonHeaders)
            "/api/timeclock/me/punch" -> respond(Json.encodeToString(PunchResult(shift, true)), HttpStatusCode.OK, jsonHeaders)
            "/api/timeclock/timesheet", "/api/timeclock/me/timesheet" -> respond(Json.encodeToString(Timesheet("2026-10-01", "2026-10-31")), HttpStatusCode.OK, jsonHeaders)
            "/api/timeclock/entry/add" -> respond("That overlaps their other time", HttpStatusCode.BadRequest)
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val reports = ReportRemoteRepository(client).apply { serverUrl = "http://localhost" }
    private val appointments = AppointmentRemoteRepository(client).apply { serverUrl = "http://localhost" }
    private val clock = TimeClockRemoteRepository(client).apply { serverUrl = "http://localhost" }

    @Test
    fun reportRoutes() = runTest {
        val report = reports.period("2026-10-01", "2026-10-31")
        assertEquals(2, (report as TmResult.Success).data.sales.invoices)
        assertIs<TmResult.Success<*>>(reports.commissions())

        val saved = reports.setCommissions(listOf(CommissionRate(Uuid.random(), 20_000)))
        assertEquals("Commission has to be from 0% to 100%", (saved as TmResult.Error).error.message)

        assertEquals(
            listOf("GET /api/report/period?from=2026-10-01&to=2026-10-31", "GET /api/report/commissions", "POST /api/report/commissions"),
            requests
        )
        assertTrue(bodies.single().startsWith("[{\"userIdentifier\""), "rates go as a list")
    }

    @Test
    fun appointmentRoutes() = runTest {
        assertIs<TmResult.Success<*>>(appointments.range("2026-10-26", "2026-11-01"))
        assertEquals(booked, (appointments.add(booked) as TmResult.Success).data)
        appointments.update(booked)
        val cancelled = appointments.setStatus(booked.identifier, AppointmentStatus.CANCELLED)
        assertEquals("It was checked in already", (cancelled as TmResult.Error).error.message)
        appointments.remove(booked.identifier)

        assertEquals(
            listOf(
                "GET /api/appointment/range?from=2026-10-26&to=2026-11-01",
                "POST /api/appointment/add",
                "POST /api/appointment/update",
                "POST /api/appointment/status/${booked.identifier}",
                "DELETE /api/appointment/remove/${booked.identifier}"
            ),
            requests
        )
        assertEquals("""{"status":"CANCELLED"}""", bodies.last())
    }

    @Test
    fun timeClockRoutes() = runTest {
        assertNull((clock.me() as TmResult.Success).data.entry)
        assertTrue((clock.mePunch() as TmResult.Success).data.clockedIn)
        clock.myTimesheet("2026-10-01", "2026-10-31")
        clock.now()
        clock.timesheet("2026-10-01", "2026-10-31", shift.userIdentifier)
        assertEquals("That overlaps their other time", (clock.add(shift) as TmResult.Error).error.message)
        clock.update(shift)
        clock.remove(shift.identifier)

        assertEquals(
            listOf(
                "GET /api/timeclock/me",
                "POST /api/timeclock/me/punch",
                "GET /api/timeclock/me/timesheet?from=2026-10-01&to=2026-10-31",
                "GET /api/timeclock/now",
                "GET /api/timeclock/timesheet?from=2026-10-01&to=2026-10-31&user=${shift.userIdentifier}",
                "POST /api/timeclock/entry/add",
                "POST /api/timeclock/entry/update",
                "DELETE /api/timeclock/entry/remove/${shift.identifier}"
            ),
            requests
        )
    }
}

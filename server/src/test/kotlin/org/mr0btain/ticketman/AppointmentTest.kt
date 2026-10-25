package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.*
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.notify.NotificationEvent
import org.mr0btain.ticketman.data.notify.NotificationSettings
import org.mr0btain.ticketman.data.notify.SmtpSettings
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

/**
 * Booking appointments, the calendar, turning one into a check in, and its reminder
 */
class AppointmentTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", name = "Tess Tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)
    private val jane = Customer(name = "Jane Doe", email = "jane@example.com", notifyEmail = true)
    private val bob = Customer(name = "Bob Smith")
    private val hour = 60L * 60 * 1000

    private lateinit var token: String
    private lateinit var adminToken: String
    private lateinit var clerkToken: String

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
        token = login(tech)
        adminToken = login(admin)
        clerkToken = login(clerk)
        post("/api/data/customer/add", json.encodeToString(jane))
        post("/api/data/customer/add", json.encodeToString(bob))
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.book(appointment: Appointment): Appointment {
        val response = post("/api/appointment/add", json.encodeToString(appointment))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.range(from: LocalDate, to: LocalDate = from, as_: String? = null): List<Appointment> {
        val response = client.get("/api/appointment/range?from=$from&to=$to") { bearerAuth(as_ ?: token) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private fun at(day: LocalDate, hourOfDay: Int): Long =
        day.atTime(hourOfDay, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val tomorrow = LocalDate.now().plusDays(1)

    @Test
    fun bookingPutsItOnTheCalendar() = testApplication {
        setup()
        val techUser = UserDatabase().getByUsername("tech")!!
        val booked = book(Appointment(startsAt = at(tomorrow, 10), customerIdentifier = jane.identifier, device = Device("Apple", "iPhone 13"), problem = "Cracked screen", technicianIdentifier = techUser.identifier))
        book(Appointment(startsAt = at(tomorrow, 9), minutes = 60, customerName = "Walk In Wendy", phone = "555 0100", problem = "Battery"))
        book(Appointment(startsAt = at(tomorrow.plusDays(3), 9), customerName = "Later Larry"))

        assertEquals("Jane Doe", booked.customerName, "a linked customer's name comes from their record")
        assertEquals("Tess Tech", booked.technicianName)
        assertEquals("tech", booked.createdBy)
        assertEquals(AppointmentStatus.BOOKED, booked.status)

        // Earliest first, and only the days asked for. Restricted users can look
        assertEquals(listOf("Walk In Wendy", "Jane Doe"), range(tomorrow, as_ = clerkToken).map { it.customerName })
        assertEquals(3, range(tomorrow, tomorrow.plusDays(3)).size)

        // But not book
        assertEquals(HttpStatusCode.Forbidden, post("/api/appointment/add", json.encodeToString(Appointment(startsAt = at(tomorrow, 11), customerName = "X")), clerkToken).status)
    }

    @Test
    fun badAppointmentsAreRefused() = testApplication {
        setup()
        val clerkUser = UserDatabase().getByUsername("clerk")!!
        assertEquals(HttpStatusCode.BadRequest, post("/api/appointment/add", json.encodeToString(Appointment(customerName = "No time"))).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/appointment/add", json.encodeToString(Appointment(startsAt = at(tomorrow, 9)))).status, "no one")
        assertEquals(HttpStatusCode.BadRequest, post("/api/appointment/add", json.encodeToString(Appointment(startsAt = at(tomorrow, 9), customerName = "A", minutes = 2))).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/appointment/add", json.encodeToString(Appointment(startsAt = at(tomorrow, 9), customerIdentifier = clerkUser.identifier))).status, "not a customer")
        assertEquals(HttpStatusCode.BadRequest, post("/api/appointment/add", json.encodeToString(Appointment(startsAt = at(tomorrow, 9), customerName = "A", technicianIdentifier = clerkUser.identifier))).status, "a Restricted tech")
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/appointment/range?from=2026-01-01&to=2026-06-01") { bearerAuth(token) }.status, "too many days")
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/appointment/range?from=2026-01-02&to=2026-01-01") { bearerAuth(token) }.status)
    }

    @Test
    fun changingCancellingAndNoShows() = testApplication {
        setup()
        val booked = book(Appointment(startsAt = at(tomorrow, 10), customerName = "Walk In Wendy"))

        val moved = post("/api/appointment/update", json.encodeToString(booked.copy(startsAt = at(tomorrow, 14), notes = "Bringing charger", status = AppointmentStatus.ARRIVED)))
        assertEquals(HttpStatusCode.OK, moved.status)
        val saved = json.decodeFromString<Appointment>(moved.bodyAsText())
        assertEquals(at(tomorrow, 14), saved.startsAt)
        assertEquals(AppointmentStatus.BOOKED, saved.status, "an update doesn't change the status")

        assertEquals(HttpStatusCode.BadRequest, post("/api/appointment/status/${booked.identifier}", json.encodeToString(AppointmentStatusRequest(AppointmentStatus.ARRIVED))).status)
        val noShow = post("/api/appointment/status/${booked.identifier}", json.encodeToString(AppointmentStatusRequest(AppointmentStatus.NO_SHOW)))
        assertEquals(AppointmentStatus.NO_SHOW, json.decodeFromString<Appointment>(noShow.bodyAsText()).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/appointment/update", json.encodeToString(saved)).status, "only booked ones change")

        post("/api/appointment/status/${booked.identifier}", json.encodeToString(AppointmentStatusRequest(AppointmentStatus.BOOKED)))
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/appointment/remove/${booked.identifier}") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/appointment/get/${booked.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun checkingInMarksItArrivedWithTheTicket() = testApplication {
        setup()
        val walkIn = book(Appointment(startsAt = at(LocalDate.now(), 10), customerName = "Bob", problem = "Battery"))
        val janes = book(Appointment(startsAt = at(LocalDate.now(), 11), customerIdentifier = jane.identifier))

        fun request(appointment: Appointment, customer: Customer) =
            CheckInRequest(Ticket(device = Device("Apple", "iPhone"), problem = "Battery"), customer.identifier, appointmentIdentifier = appointment.identifier)

        // Jane's appointment can't be checked in for Bob
        assertEquals(HttpStatusCode.BadRequest, post("/api/counter/check-in", json.encodeToString(request(janes, bob))).status)

        // Someone new gets linked once they're a customer
        val response = post("/api/counter/check-in", json.encodeToString(request(walkIn, bob)))
        assertEquals(HttpStatusCode.Created, response.status)
        val ticket = json.decodeFromString<CheckInResponse>(response.bodyAsText()).ticket

        val arrived = AppointmentDatabase().get(walkIn.identifier)!!
        assertEquals(AppointmentStatus.ARRIVED, arrived.status)
        assertEquals(ticket.identifier, arrived.ticketIdentifier)
        assertEquals(bob.identifier, arrived.customerIdentifier)
        assertEquals("Bob Smith", arrived.customerName)

        // Not twice, and it can't be called off after
        assertEquals(HttpStatusCode.BadRequest, post("/api/counter/check-in", json.encodeToString(request(walkIn, bob))).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/appointment/status/${walkIn.identifier}", json.encodeToString(AppointmentStatusRequest(AppointmentStatus.CANCELLED))).status)
    }

    @Test
    fun customersAreRemindedOnceTheDayBefore() = testApplication {
        setup()
        post("/api/notify/settings", json.encodeToString(NotificationSettings(
            emailEnabled = true,
            smtp = SmtpSettings(host = "mail.example.com", fromAddress = "shop@example.com"),
            events = setOf(NotificationEvent.APPOINTMENT_REMINDER)
        )), adminToken)

        val now = System.currentTimeMillis()
        val soon = book(Appointment(startsAt = now + 20 * hour, customerIdentifier = jane.identifier, device = Device("Apple", "iPad")))
        book(Appointment(startsAt = now + 30 * hour, customerIdentifier = jane.identifier))
        book(Appointment(startsAt = now + 2 * hour, customerName = "Walk In Wendy"))
        val cancelled = book(Appointment(startsAt = now + 3 * hour, customerIdentifier = jane.identifier))
        post("/api/appointment/status/${cancelled.identifier}", json.encodeToString(AppointmentStatusRequest(AppointmentStatus.CANCELLED)))

        val notifications = NotificationService(NotifyDatabase(), TicketDatabase(), CustomerDatabase(), InvoiceDatabase(), ShopDatabase(), EstimateDatabase(), AuditLogDatabase(UserDatabase()))
        assertEquals(1, notifications.queueAppointmentReminders())
        assertEquals(0, notifications.queueAppointmentReminders(), "only once")

        val message = NotifyDatabase().page(PagedRequest(0, 10))!!.single()
        assertEquals(NotificationEvent.APPOINTMENT_REMINDER, message.event)
        assertNull(message.ticketIdentifier)
        assertContains(message.body, "Hi Jane")
        assertContains(message.body, "iPad")
        assertNotNull(AppointmentDatabase().get(soon.identifier)!!.reminderSentAt)

        // Moving it sends it again
        post("/api/appointment/update", json.encodeToString(AppointmentDatabase().get(soon.identifier)!!.copy(startsAt = now + 22 * hour)))
        assertEquals(1, notifications.queueAppointmentReminders())
    }
}

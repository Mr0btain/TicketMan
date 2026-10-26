package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.ScheduleDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.OnlineBooking
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi

/**
 * Customers booking a visit from the status page, into the shifts on the staff schedule
 */
@OptIn(ExperimentalUuidApi::class)
class BookingTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", name = "Nina Tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", name = "Cal Clerk", password = "clerk_pass", accessLevel = AccessLevel.NORMAL)

    private val shop = ShopProfile(name = "Fix It", phone = "555-0100", booking = OnlineBooking(enabled = true, minutes = 30, daysAhead = 7, leadMinutes = 0))
    private val tomorrow = LocalDate.now().plusDays(1)

    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(profile: ShopProfile = shop) {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
        adminToken = json.decodeFromString<AuthResponse>(client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(admin.username, admin.password)))
        }.bodyAsText()).accessToken!!
        assertEquals(HttpStatusCode.Accepted, client.post("/api/shop/update") {
            contentType(ContentType.Application.Json)
            bearerAuth(adminToken)
            setBody(json.encodeToString(profile))
        }.status)
    }

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = tomorrow) = day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun shift(user: User, from: Int, to: Int, kind: ScheduleKind = ScheduleKind.SHIFT) =
        ScheduleEntry(userIdentifier = user.identifier, kind = kind, startsAt = at(from), endsAt = at(to), allDay = kind.allDay)

    private suspend fun ApplicationTestBuilder.page(day: LocalDate = tomorrow): String {
        val response = client.get("/status/book?day=$day")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.bodyAsText()
    }

    private fun times(html: String): List<String> =
        Regex("""name="time" value="(\d+)"""").findAll(html).map { formatTime(it.groupValues[1].toLong()) }.toList()

    private suspend fun ApplicationTestBuilder.book(time: Long, name: String = "Ada Lovelace", phone: String = "555 123 4567") =
        client.submitForm("/status/book", parameters {
            append("time", time.toString())
            append("name", name)
            append("phone", phone)
            append("device", "iPhone 14")
            append("problem", "Cracked screen")
        })

    @Test
    fun timesComeFromTheShiftsLessWhatsBooked() = testApplication {
        setup()
        ScheduleDatabase().add(listOf(shift(tech, 9, 11)))
        AppointmentDatabase().add(Appointment(startsAt = at(10), minutes = 30, customerName = "Already booked"))

        assertEquals(listOf("09:00", "09:30", "10:30"), times(page()))

        // Someone else working then opens 10:00 again
        ScheduleDatabase().add(listOf(shift(clerk, 10, 12)))
        assertEquals(listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30"), times(page()))

        // Off that day takes their shift away
        ScheduleDatabase().add(listOf(ScheduleEntry(userIdentifier = clerk.identifier, kind = ScheduleKind.TIME_OFF, allDay = true, startsAt = at(0), endsAt = at(0, day = tomorrow.plusDays(1)))))
        assertEquals(listOf("09:00", "09:30", "10:30"), times(page()))
    }

    @Test
    fun bookingPutsItOnTheCalendarOnce() = testApplication {
        setup()
        ScheduleDatabase().add(listOf(shift(tech, 9, 10)))

        val booked = book(at(9, 30))
        assertEquals(HttpStatusCode.OK, booked.status, booked.bodyAsText())
        assertContains(booked.bodyAsText(), "You're booked")

        val appointment = AppointmentDatabase().between(at(0), at(23)).orEmpty().single()
        assertEquals(at(9, 30), appointment.startsAt)
        assertEquals("Ada Lovelace", appointment.customerName)
        assertEquals("iPhone 14", appointment.device.model)
        assertEquals("customer", appointment.createdBy)

        // Gone from the page, and refused if it's sent again
        assertEquals(listOf("09:00"), times(page()))
        val again = book(at(9, 30), name = "Someone else")
        assertEquals(HttpStatusCode.BadRequest, again.status)
        assertContains(again.bodyAsText(), "Someone just took that time")
        assertContains(again.bodyAsText(), "Someone else", message = "what they typed is kept")

        // A time nobody works, and no way to reach them
        assertEquals(HttpStatusCode.BadRequest, book(at(14)).status)
        assertEquals(HttpStatusCode.BadRequest, book(at(9), phone = "12").status)
    }

    @Test
    fun itsOnlyThereWhenTheShopTurnsItOn() = testApplication {
        setup(shop.copy(booking = OnlineBooking()))
        assertEquals(HttpStatusCode.NotFound, client.get("/status/book").status)
        assertEquals(HttpStatusCode.NotFound, book(at(9)).status)
        assertFalse(client.get("/status").bodyAsText().contains("Book a visit"))
    }

    @Test
    fun theLookupFormLinksToIt() = testApplication {
        setup()
        assertContains(client.get("/status").bodyAsText(), """href="status/book"""")
        // With no shifts there's nothing to pick, and it says to call
        assertContains(page(), "no times free")
    }
}

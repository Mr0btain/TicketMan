package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.PurchaseDatabase
import org.mr0btain.ticketman.components.database.ScheduleDatabase
import org.mr0btain.ticketman.components.database.TimeClockDatabase
import org.mr0btain.ticketman.components.database.TimeOffDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.debug.DEMO_STAFF
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.server.request.LoginRequest
import kotlin.test.*

/** SEED_TEST_DATA filling an empty database for trying the app out */
class SeedTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private fun ticketCount() = TicketDatabase().count()

    @Test
    fun anEmptyDatabaseIsFilledLikeAShopsDay() = testApplication {
        configureTestServer(dataDir, extraConfig = mapOf("env.seedTestData" to "true"))
        startApplication()

        assertEquals(150, ticketCount())
        assertEquals("Bench Repair (test data)", ShopDatabase().get().name)

        val invoices = InvoiceDatabase().getPagedItems(PagedRequest(offset = 0, itemsPerPage = 500))
        assertTrue(invoices.size > 50, "about two thirds of the tickets get work, ${invoices.size} did")
        assertTrue(invoices.all { it.charges.isNotEmpty() && it.totalCents > 0 })
        assertTrue(invoices.any { it.status == InvoiceStatus.PAID })
        assertTrue(invoices.any { it.status == InvoiceStatus.OPEN && it.paidCents > 0 }, "some took a deposit")

        // Paid ones are the picked up ones
        val tickets = TicketDatabase()
        invoices.filter { it.status == InvoiceStatus.PAID }.forEach { invoice ->
            assertEquals(TicketStatusType.PICKED_UP, tickets.getById(invoice.ticketIdentifier!!)!!.status.status)
            assertEquals(0, invoice.balanceCents)
        }

        // Every level can be tried, and no mock user has the access level only migrations use
        val users = UserDatabase().getPagedItems(PagedRequest(offset = 0, itemsPerPage = 100))!!
        assertTrue(users.none { it.accessLevel == AccessLevel.NONE })
        DEMO_STAFF.forEach { staff ->
            val login = client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(LoginRequest(staff.username, staff.password)))
            }
            assertEquals(HttpStatusCode.Accepted, login.status, staff.username)
        }

        // What the shop runs its days with: everyone on the schedule, time off, the clock, the calendar, orders
        val now = System.currentTimeMillis()
        val week = 7 * 86_400_000L
        val schedule = ScheduleDatabase().between(now - week, now + week).orEmpty()
        val tech = UserDatabase().getByUsername(DEMO_STAFF.first().username)!!
        assertTrue(schedule.any { it.kind == ScheduleKind.SHIFT && it.userIdentifier == tech.identifier && it.seriesIdentifier != null })
        assertTrue(schedule.filter { it.kind == ScheduleKind.SHIFT }.map { it.userIdentifier }.toSet().size >= DEMO_STAFF.size + 1)

        val requests = TimeOffDatabase().list().orEmpty()
        assertEquals(setOf(TimeOffStatus.PENDING, TimeOffStatus.APPROVED, TimeOffStatus.DECLINED), requests.map { it.status }.toSet())
        assertTrue(TimeOffDatabase().allowance(tech.identifier) > 0)
        assertTrue(!TimeClockDatabase().between(now - 2 * week, now).isNullOrEmpty(), "the past shifts were clocked")

        val appointments = AppointmentDatabase().between(now - 2 * week, now + 2 * week).orEmpty()
        assertTrue(appointments.any { it.status == AppointmentStatus.BOOKED && it.startsAt > now })
        assertTrue(appointments.any { it.status == AppointmentStatus.ARRIVED && it.ticketIdentifier != null })
        assertTrue(ShopDatabase().get().booking.enabled)

        assertEquals(3, PurchaseDatabase().suppliers()?.size)
        val orders = PurchaseDatabase().orders(PagedRequest(offset = 0, itemsPerPage = 50)).orEmpty().map { it.status }.toSet()
        assertTrue(orders.containsAll(listOf(PurchaseStatus.DRAFT, PurchaseStatus.ORDERED, PurchaseStatus.PARTLY_RECEIVED, PurchaseStatus.RECEIVED)), orders.toString())
    }

    @Test
    fun itsOffUnlessAskedForAndNeverAddsToRecordsAlreadyThere() {
        testApplication {
            configureTestServer(dataDir)
            startApplication()
            assertEquals(0, ticketCount())
            TicketDatabase().add(org.mr0btain.ticketman.data.ticket.Ticket())
        }
        try {
            transaction { exec("SHUTDOWN") }
        } catch (_: Exception) {
            // Committing after SHUTDOWN fails, the database is closed either way
        }

        testApplication {
            configureTestServer(dataDir, extraConfig = mapOf("env.seedTestData" to "true"))
            startApplication()
            assertEquals(1, ticketCount())
        }
    }
}

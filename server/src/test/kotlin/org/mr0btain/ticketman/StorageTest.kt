package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.*
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.components.service.StorageService
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.shop.Storage
import org.mr0btain.ticketman.data.shop.StorageTerms
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import kotlin.test.*

/**
 * Storage fees on devices left after they're ready, and devices treated as abandoned
 */
class StorageTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val jane = Customer(name = "Jane Doe", email = "jane@example.com", notifyEmail = true)
    private val terms = StorageTerms(feeAfterDays = 3, dailyFeeCents = 200, abandonAfterDays = 30)
    private val day = 24L * 60 * 60 * 1000

    private lateinit var token: String
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
        token = login(tech)
        adminToken = login(admin)
        post("/api/data/customer/add", json.encodeToString(jane))
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(ShopProfile(name = "Fix It", storage = terms)), adminToken).status)
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

    /** A ticket that's been ready for [days] */
    private suspend fun ApplicationTestBuilder.readyFor(days: Int): Ticket {
        val ticket = json.decodeFromString<CheckInResponse>(
            post("/api/counter/check-in", json.encodeToString(CheckInRequest(Ticket(device = Device("Apple", "iPhone"), problem = "Screen"), jane.identifier))).bodyAsText()
        ).ticket
        val ready = ticket.copy(status = TicketStatus(TicketStatusType.DONE, System.currentTimeMillis() - days * day - 60_000))
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(ready)).status)
        return TicketDatabase().getById(ticket.identifier)!!
    }

    private suspend fun ApplicationTestBuilder.open(ticket: Ticket): Invoice =
        json.decodeFromString(post("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())

    @Test
    fun theFeeIsADayForEachDayPastTheFreeOnes() = testApplication {
        setup()

        assertTrue(open(readyFor(2)).charges.isEmpty(), "still in the free days")

        val ticket = readyFor(5)
        val invoice = open(ticket)
        val line = invoice.charges.single()
        assertEquals(Storage.lineIdentifier(ticket.identifier), line.identifier)
        assertEquals("Storage, 2 days", line.description)
        assertEquals(400, invoice.totalCents)
        assertFalse(line.taxable)

        // Opening it again the same day changes nothing, and pickup has to cover it
        assertEquals(invoice.charges, open(ticket).charges)
        val short = post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, 300)))))
        assertEquals(HttpStatusCode.Conflict, short.status)
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, 400))))).status)
    }

    @Test
    fun takingTheLineOffWaivesIt() = testApplication {
        setup()
        val ticket = readyFor(10)
        val invoice = open(ticket)

        val waived = post("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChanges.between(invoice.charges, 0, emptyList(), 0)))
        assertTrue(json.decodeFromString<Invoice>(waived.bodyAsText()).storageWaived)
        assertTrue(open(ticket).charges.isEmpty(), "it isn't put back")
    }

    @Test
    fun leftLongEnoughItsAbandonedAndTheCustomerIsTold() = testApplication {
        setup()
        post("/api/notify/settings", json.encodeToString(NotificationSettings(
            emailEnabled = true,
            smtp = SmtpSettings(host = "mail.example.com", fromAddress = "shop@example.com"),
            events = setOf(NotificationEvent.STORAGE_FEE, NotificationEvent.ABANDONED)
        )), adminToken)

        val fresh = readyFor(2)
        val old = readyFor(31)
        val notifications = NotificationService(NotifyDatabase(), TicketDatabase(), CustomerDatabase(), InvoiceDatabase(), ShopDatabase(), EstimateDatabase(), AuditLogDatabase(UserDatabase()))
        val storage = StorageService(TicketDatabase(), ShopDatabase(), notifications, AuditLogDatabase(UserDatabase()))

        assertEquals(1, storage.sweep())
        assertEquals(0, storage.sweep(), "only once")
        assertNotNull(TicketDatabase().getById(old.identifier)!!.abandonedAt)
        assertNull(TicketDatabase().getById(fresh.identifier)!!.abandonedAt)

        // Out of the ready list, into its own
        val queue = json.decodeFromString<CounterQueueResponse>(client.get("/api/counter/queue") { bearerAuth(token) }.bodyAsText())
        assertEquals(listOf(fresh.identifier), queue.readyForPickup.map { it.ticket.identifier })
        assertEquals(listOf(old.identifier), queue.abandoned.map { it.ticket.identifier })

        // The fresh one was told its fee starts tomorrow, the old one that it's abandoned
        val outbox = NotifyDatabase().page(PagedRequest(0, 50))!!
        assertEquals(setOf(fresh.identifier to NotificationEvent.STORAGE_FEE, old.identifier to NotificationEvent.ABANDONED, old.identifier to NotificationEvent.STORAGE_FEE),
            outbox.map { it.ticketIdentifier to it.event }.toSet())
        assertContains(outbox.first { it.event == NotificationEvent.STORAGE_FEE }.body, "$2.00 a day")

        // Moving it on by hand undoes it
        val reopened = TicketDatabase().getById(old.identifier)!!.copy(status = TicketStatus(TicketStatusType.IN_PROGRESS))
        post("/api/data/ticket/update", json.encodeToString(reopened))
        assertNull(TicketDatabase().getById(old.identifier)!!.abandonedAt)
    }

    @Test
    fun theTermsAreChecked() = testApplication {
        setup()
        assertEquals(HttpStatusCode.BadRequest, post("/api/shop/update", json.encodeToString(ShopProfile(storage = terms.copy(abandonAfterDays = 2))), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/shop/update", json.encodeToString(ShopProfile(storage = terms.copy(dailyFeeCents = -1))), adminToken).status)

        assertEquals(
            "Devices left more than 3 days after they're ready are charged $2.00 a day for storage. Devices not collected within 30 days of being ready are considered abandoned.",
            Storage.termsText(terms, "$")
        )
        assertNull(Storage.termsText(StorageTerms(), "$"))
    }
}

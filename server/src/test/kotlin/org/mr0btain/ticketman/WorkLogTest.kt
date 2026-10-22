package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.data.invoice.InvoiceChanges
import org.mr0btain.ticketman.data.invoice.Charge
import kotlin.test.*

/**
 * A ticket's work log, and the invoice lines it keeps in step
 */
class WorkLogTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", name = "Tess Tech", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)
    private val screen = Inventory(name = "iPhone 14 Screen", stock = 3, priceCents = 12_000)

    private lateinit var token: String
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
        clerkToken = login(clerk)
        post("/api/shop/update", json.encodeToString(ShopProfile(taxRate = "10", laborRateCents = 8_000)), login(admin))
        post("/api/data/inventory/add", json.encodeToString(screen))
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String = token) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.newTicket(): Ticket =
        json.decodeFromString(post("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

    private suspend fun ApplicationTestBuilder.log(ticket: Ticket, request: WorkRequest, as_: String = token) =
        post("/api/data/ticket/add/work/${ticket.identifier}", json.encodeToString(request), as_)

    private suspend fun ApplicationTestBuilder.invoiceOf(ticket: Ticket): Invoice =
        json.decodeFromString(client.get("/api/invoice/ticket/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.workOf(ticket: Ticket, as_: String = token): List<WorkEntry> =
        json.decodeFromString(client.get("/api/data/ticket/get/work/${ticket.identifier}") { bearerAuth(as_) }.bodyAsText())

    @Test
    fun timeIsChargedAtTheShopsRateAndPartsAtTheirPrice() = testApplication {
        setup()
        val ticket = newTicket()

        val labor = log(ticket, WorkRequest(WorkType.LABOR, "Replaced the screen", quantity = 1.5))
        assertEquals(HttpStatusCode.Created, labor.status)
        val time = json.decodeFromString<WorkEntry>(labor.bodyAsText())
        assertEquals(8_000, time.unitPriceCents)
        assertEquals("tech", time.userName)
        assertTrue(time.workedAt > 0)

        val part = json.decodeFromString<WorkEntry>(log(ticket, WorkRequest(WorkType.PART, quantity = 1.0, inventoryIdentifier = screen.identifier)).bodyAsText())
        assertEquals("iPhone 14 Screen", part.description)
        assertEquals(12_000, part.unitPriceCents)

        // The invoice was made by the first entry, and has a line for each with the entry's identifier
        val invoice = invoiceOf(ticket)
        val laborLine = invoice.charges.single { it.identifier == time.identifier }
        assertEquals(ChargeType.LABOR, laborLine.type)
        assertFalse(laborLine.taxable)
        val partLine = invoice.charges.single { it.identifier == part.identifier }
        assertEquals(ChargeType.PART, partLine.type)
        assertEquals(screen.identifier, partLine.inventoryIdentifier)
        // $120 + $120 labor, 10% tax on the part only
        assertEquals(12_000 + 12_000 + 1_200, invoice.totalCents)

        assertEquals(listOf(time.identifier, part.identifier), workOf(ticket, clerkToken).map { it.identifier })
    }

    /** The counter saving what it changed since it opened the invoice */
    private suspend fun ApplicationTestBuilder.counterSaves(opened: Invoice, charges: List<Charge>, discount: Long = opened.discountCents) =
        post("/api/invoice/${opened.identifier}/update", json.encodeToString(InvoiceChanges.between(opened.charges, opened.discountCents, charges, discount)))

    @Test
    fun workLoggedWhileTheCounterHasTheInvoiceOpenIsKept() = testApplication {
        setup()
        val ticket = newTicket()
        val first = json.decodeFromString<WorkEntry>(log(ticket, WorkRequest(WorkType.LABOR, "Diagnosis", quantity = 0.5)).bodyAsText())
        val atCounter = invoiceOf(ticket)

        // A technician logs more while the counter is busy with it
        val more = json.decodeFromString<WorkEntry>(log(ticket, WorkRequest(WorkType.LABOR, "Replaced the screen", quantity = 1.0)).bodyAsText())

        val cable = Charge(description = "Cable", unitPriceCents = 900)
        val cheaper = atCounter.charges.single().copy(unitPriceCents = 6_000)
        val saved = counterSaves(atCounter, listOf(cheaper, cable), discount = 500)
        assertEquals(HttpStatusCode.OK, saved.status)

        val invoice = json.decodeFromString<Invoice>(saved.bodyAsText())
        assertEquals(listOf(first.identifier, more.identifier, cable.identifier), invoice.charges.map { it.identifier })
        assertEquals(6_000, invoice.charges.first().unitPriceCents)
        assertEquals(500, invoice.discountCents)
    }

    @Test
    fun aLineChangedOnBothSidesIsRefused() = testApplication {
        setup()
        val ticket = newTicket()
        log(ticket, WorkRequest(WorkType.LABOR, "Diagnosis", quantity = 0.5))
        val front = invoiceOf(ticket)
        val back = invoiceOf(ticket)

        assertEquals(HttpStatusCode.OK, counterSaves(front, listOf(front.charges.single().copy(unitPriceCents = 5_000))).status)

        val refused = counterSaves(back, listOf(back.charges.single().copy(unitPriceCents = 7_000)))
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals("Someone else changed \"Diagnosis\" on this invoice", refused.bodyAsText())
        assertEquals(5_000, invoiceOf(ticket).charges.single().unitPriceCents)

        // Removing a line someone else changed is refused too
        assertEquals(HttpStatusCode.Conflict, counterSaves(back, emptyList()).status)
    }

    @Test
    fun aPriceGivenWinsAndRemovingTakesTheLineOff() = testApplication {
        setup()
        val ticket = newTicket()

        val entry = json.decodeFromString<WorkEntry>(log(ticket, WorkRequest(WorkType.LABOR, "Diagnosis", quantity = 0.25, unitPriceCents = 4_000)).bodyAsText())
        assertEquals(1_000, invoiceOf(ticket).totalCents)

        val removed = client.delete("/api/data/ticket/remove/work/${entry.identifier}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.Accepted, removed.status)
        assertTrue(invoiceOf(ticket).charges.isEmpty())
        assertTrue(workOf(ticket).isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/data/ticket/remove/work/${entry.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun stockIsTakenAtPickupAndThenTheWorkCantChange() = testApplication {
        setup()
        val ticket = newTicket()
        val part = json.decodeFromString<WorkEntry>(log(ticket, WorkRequest(WorkType.PART, quantity = 2.0, inventoryIdentifier = screen.identifier)).bodyAsText())

        val stockBefore = json.decodeFromString<Inventory>(client.get("/api/data/inventory/get/${screen.identifier}") { bearerAuth(token) }.bodyAsText()).stock
        assertEquals(3, stockBefore)

        val owed = invoiceOf(ticket).totalCents
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, owed))))).status)
        assertEquals(1, json.decodeFromString<Inventory>(client.get("/api/data/inventory/get/${screen.identifier}") { bearerAuth(token) }.bodyAsText()).stock)

        assertEquals(HttpStatusCode.Conflict, log(ticket, WorkRequest(WorkType.LABOR, "More", quantity = 1.0)).status)
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/data/ticket/remove/work/${part.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun badWorkIsRefused() = testApplication {
        setup()
        val ticket = newTicket()

        assertEquals(HttpStatusCode.BadRequest, log(ticket, WorkRequest(WorkType.PART, quantity = 1.0)).status)
        assertEquals(HttpStatusCode.BadRequest, log(ticket, WorkRequest(WorkType.PART, quantity = 1.5, inventoryIdentifier = screen.identifier)).status)
        assertEquals(HttpStatusCode.BadRequest, log(ticket, WorkRequest(WorkType.LABOR, "", quantity = 1.0)).status)
        assertEquals(HttpStatusCode.BadRequest, log(ticket, WorkRequest(WorkType.LABOR, "Time", quantity = 0.0)).status)
        assertEquals(HttpStatusCode.BadRequest, log(ticket, WorkRequest(WorkType.LABOR, "Time", quantity = 1.0, unitPriceCents = -1)).status)
        assertEquals(HttpStatusCode.NotFound, log(Ticket(), WorkRequest(WorkType.LABOR, "Time", quantity = 1.0)).status)
        assertTrue(workOf(ticket).isEmpty())
    }

    @Test
    fun restrictedUsersCanLookButNotLog() = testApplication {
        setup()
        val ticket = newTicket()

        assertEquals(HttpStatusCode.Forbidden, log(ticket, WorkRequest(WorkType.LABOR, "Time", quantity = 1.0), clerkToken).status)
        assertEquals(HttpStatusCode.OK, client.get("/api/data/ticket/get/work/${ticket.identifier}") { bearerAuth(clerkToken) }.status)
    }

    @Test
    fun deletingTheTicketTakesItsWork() = testApplication {
        setup()
        val ticket = newTicket()
        val entry = json.decodeFromString<WorkEntry>(log(ticket, WorkRequest(WorkType.LABOR, "Time", quantity = 1.0)).bodyAsText())

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/data/ticket/remove/${ticket.identifier}") { bearerAuth(token) }.status)

        // Its invoice goes with it (a cascade), and so does its work
        assertEquals(HttpStatusCode.NotFound, client.get("/api/invoice/ticket/${ticket.identifier}") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/data/ticket/remove/work/${entry.identifier}") { bearerAuth(token) }.status)
    }
}

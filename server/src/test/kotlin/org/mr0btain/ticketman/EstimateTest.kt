package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.notify.EmailSender
import org.mr0btain.ticketman.components.notify.NotificationSenders
import org.mr0btain.ticketman.components.notify.SendResult
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.estimate.*
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.EstimateDecisionRequest
import org.mr0btain.ticketman.server.request.EstimateRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Estimates: sending, the customer's answer on the status page or through staff, and what approving does to the
 * invoice and the ticket
 */
@OptIn(ExperimentalUuidApi::class)
class EstimateTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)

    private val awaiting = CustomStatus(name = "Awaiting approval", actsAs = TicketStatusType.ON_HOLD)
    private val declined = CustomStatus(name = "Declined, return", actsAs = TicketStatusType.DONE)
    private val shop = ShopProfile(
        name = "Fix It Shop", taxRate = "10", publicUrl = "https://repairs.example.com", statuses = listOf(awaiting, declined),
        estimateSentStatus = awaiting.choice,
        estimateApprovedStatus = StatusChoice(TicketStatusType.IN_PROGRESS),
        estimateDeclinedStatus = declined.choice
    )

    private val labor = Charge(type = ChargeType.LABOR, description = "Screen swap", quantity = 1.0, unitPriceCents = 5000, taxable = false)

    private lateinit var adminToken: String
    private lateinit var token: String
    private val emails = mutableListOf<String>()

    @BeforeTest
    fun fakeEmail() {
        NotificationSenders.email = EmailSender { _, _, subject, _ -> synchronized(emails) { emails += subject }; SendResult.Sent }
    }

    @AfterTest
    fun cleanup() {
        NotificationSenders.reset()
        cleanupTestDataDir(dataDir)
    }

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(viewer)
        }
        adminToken = login(admin)
        token = login(tech)

        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(shop), adminToken).status)
        val notify = NotificationSettings(
            emailEnabled = true,
            smtp = SmtpSettings(host = "mail.example.com", fromAddress = "shop@example.com"),
            events = setOf(NotificationEvent.ESTIMATE_READY, NotificationEvent.STATUS_CHANGED),
            // Every status, to show estimate moves don't send it
            statusChangeStatuses = shop.statuses.selectable()
        )
        assertEquals(HttpStatusCode.OK, post("/api/notify/settings", json.encodeToString(notify), adminToken).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String = token) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.ticket(identifier: Uuid): Ticket =
        json.decodeFromString(client.get("/api/data/ticket/get/$identifier") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.estimates(ticket: Uuid): List<Estimate> =
        json.decodeFromString(client.get("/api/estimate/ticket/$ticket") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.invoice(ticket: Uuid): Invoice =
        json.decodeFromString(post("/api/invoice/ticket/$ticket", "").bodyAsText())

    private suspend fun ApplicationTestBuilder.messages(ticket: Uuid): List<OutboxMessage> =
        json.decodeFromString(client.get("/api/notify/ticket/$ticket") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.newTicket(): Ticket {
        val customer = Customer(name = "Jane Doe", email = "jane@home.test", phone = "5551234567", notifyEmail = true)
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer)).status)

        val ticket = Ticket(device = Device("Apple", "iPhone 14"))
        val added = post("/api/data/ticket/add", json.encodeToString(ticket))
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/customer/${ticket.identifier}", json.encodeToString(customer.identifier)).status)
        return json.decodeFromString(added.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.send(ticket: Ticket, lines: List<Charge>, note: String = ""): Estimate {
        val response = post("/api/estimate/ticket/${ticket.identifier}", json.encodeToString(EstimateRequest(lines, note)))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.answerOnPage(ticket: Ticket, decision: String, name: String) =
        createClient { followRedirects = false }.submitForm(
            url = "/status/${ticket.statusToken}/estimate",
            formParameters = parameters {
                append("decision", decision)
                append("name", name)
            }
        )

    // ------------------------------------------ Sending ------------------------------------------

    @Test
    fun sendingOneMovesTheTicketTellsTheCustomerAndReplacesTheLast() = testApplication {
        setup()
        val ticket = newTicket()

        val first = send(ticket, listOf(labor))
        assertEquals(EstimateStatus.PENDING, first.status)
        assertEquals(tech.username, first.createdBy)
        assertEquals(1000, first.taxRateBasisPoints)
        assertEquals(awaiting.choice, ticket(ticket.identifier).status.choice)

        // Only the estimate's own message, not one for the status it moved to
        assertEquals(listOf(NotificationEvent.ESTIMATE_READY), messages(ticket.identifier).map { it.event })
        assertTrue("$50.00" in messages(ticket.identifier).single().body)

        val second = send(ticket, listOf(labor.copy(unitPriceCents = 6000)))
        assertEquals(listOf(second.identifier to EstimateStatus.PENDING, first.identifier to EstimateStatus.WITHDRAWN), estimates(ticket.identifier).map { it.identifier to it.status })
    }

    @Test
    fun badEstimatesAndViewersAreRefused() = testApplication {
        setup()
        val ticket = newTicket()

        listOf(
            emptyList(),
            listOf(labor.copy(description = " ")),
            listOf(labor.copy(quantity = 0.0)),
            listOf(Charge(type = ChargeType.PART, description = "Screen", unitPriceCents = 100))
        ).forEach { lines -> assertEquals(HttpStatusCode.BadRequest, post("/api/estimate/ticket/${ticket.identifier}", json.encodeToString(EstimateRequest(lines))).status, lines.toString()) }

        assertEquals(HttpStatusCode.Forbidden, post("/api/estimate/ticket/${ticket.identifier}", json.encodeToString(EstimateRequest(listOf(labor))), login(viewer)).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/estimate/ticket/${Uuid.random()}", json.encodeToString(EstimateRequest(listOf(labor)))).status)

        // A shop can't point estimates at a status staff couldn't pick
        val retiredStatus = shop.copy(estimateSentStatus = StatusChoice(TicketStatusType.PICKED_UP))
        assertEquals(HttpStatusCode.BadRequest, post("/api/shop/update", json.encodeToString(retiredStatus), adminToken).status)
    }

    // ------------------------------------------ Answers ------------------------------------------

    @Test
    fun staffWritingDownAnApprovalPutsItOnTheInvoiceOnce() = testApplication {
        setup()
        val ticket = newTicket()
        val part = Inventory(name = "iPhone 14 Screen", stock = 3, priceCents = 12000)
        assertEquals(HttpStatusCode.Created, post("/api/data/inventory/add", json.encodeToString(part)).status)
        val screen = Charge(type = ChargeType.PART, description = "iPhone 14 Screen", unitPriceCents = 12000, inventoryIdentifier = part.identifier)

        val estimate = send(ticket, listOf(screen, labor))
        val decide = EstimateDecisionRequest(approved = true, method = DecisionMethod.PHONE, note = "Said go ahead")
        val decided = post("/api/estimate/${estimate.identifier}/decide", json.encodeToString(decide))
        assertEquals(HttpStatusCode.OK, decided.status, decided.bodyAsText())

        val saved = estimates(ticket.identifier).single()
        assertEquals(EstimateStatus.APPROVED, saved.status)
        assertEquals(DecisionMethod.PHONE, saved.decision!!.method)
        assertEquals(tech.username, saved.decision!!.recordedBy)
        assertEquals(12000 + 1200 + 5000L, saved.decision!!.amountCents)

        // Lines on the invoice with new ids, the quoted price and status follow
        val lines = invoice(ticket.identifier).charges
        assertEquals(listOf("iPhone 14 Screen", "Screen swap"), lines.map { it.description })
        assertTrue(lines.none { it.identifier in estimate.lines.map { line -> line.identifier } })
        assertEquals(18200L, ticket(ticket.identifier).estimateCents)
        assertEquals(StatusChoice(TicketStatusType.IN_PROGRESS), ticket(ticket.identifier).status.choice)

        // A second answer doesn't count
        assertEquals(HttpStatusCode.Conflict, post("/api/estimate/${estimate.identifier}/decide", json.encodeToString(decide)).status)
        assertEquals(2, invoice(ticket.identifier).charges.size)

        // Staff can't pretend it was the customer on the page
        val other = send(ticket, listOf(labor))
        assertEquals(HttpStatusCode.BadRequest, post("/api/estimate/${other.identifier}/decide", json.encodeToString(decide.copy(method = DecisionMethod.STATUS_PAGE))).status)
    }

    @Test
    fun theCustomerApprovesOnTheirPageWithTheirName() = testApplication {
        setup()
        val ticket = newTicket()
        send(ticket, listOf(labor), note = "The screen and the frame are bent")

        val page = client.get("/status/${ticket.statusToken}").bodyAsText()
        listOf("please approve or decline", "The screen and the frame are bent", "Screen swap", "$50.00", """value="approve"""").forEach { assertTrue(it in page, it) }

        // A name is the signature
        val unsigned = answerOnPage(ticket, "approve", " ")
        assertEquals(HttpStatusCode.Conflict, unsigned.status)
        assertTrue("Type your full name" in unsigned.bodyAsText())
        assertEquals(EstimateStatus.PENDING, estimates(ticket.identifier).single().status)

        val signed = answerOnPage(ticket, "approve", "Jane Doe")
        assertEquals(HttpStatusCode.SeeOther, signed.status)
        assertEquals("../${ticket.statusToken}", signed.headers[HttpHeaders.Location])

        val decision = estimates(ticket.identifier).single().decision!!
        assertEquals(DecisionMethod.STATUS_PAGE, decision.method)
        assertEquals("Jane Doe", decision.signedName)
        assertEquals("", decision.recordedBy)
        assertEquals(listOf("Screen swap"), invoice(ticket.identifier).charges.map { it.description })

        val after = client.get("/status/${ticket.statusToken}").bodyAsText()
        assertTrue("You approved the estimate of" in after)
        assertFalse("""value="approve"""" in after)

        // Nothing left to answer
        assertEquals(HttpStatusCode.Conflict, answerOnPage(ticket, "decline", "Jane Doe").status)
        assertEquals(HttpStatusCode.NotFound, createClient { followRedirects = false }.submitForm("/status/${"0".repeat(32)}/estimate", parameters { append("decision", "approve"); append("name", "Jane") }).status)
    }

    @Test
    fun decliningLeavesTheInvoiceAloneAndMovesTheTicket() = testApplication {
        setup()
        val ticket = newTicket()
        send(ticket, listOf(labor))

        assertEquals(HttpStatusCode.SeeOther, answerOnPage(ticket, "decline", "Jane Doe").status)

        assertEquals(EstimateStatus.DECLINED, estimates(ticket.identifier).single().status)
        assertTrue(invoice(ticket.identifier).charges.isEmpty())
        assertEquals(declined.choice, ticket(ticket.identifier).status.choice)
        assertTrue("You declined" in client.get("/status/${ticket.statusToken}").bodyAsText())
    }

    @Test
    fun anApprovalThatCantGoOnTheInvoiceLeavesTheEstimateWaiting() = testApplication {
        setup()
        val ticket = newTicket()
        val estimate = send(ticket, listOf(labor))

        // The invoice is voided meanwhile
        val invoice = invoice(ticket.identifier)
        assertEquals(HttpStatusCode.OK, post("/api/invoice/${invoice.identifier}/void", "", adminToken).status)

        val decide = EstimateDecisionRequest(approved = true, method = DecisionMethod.IN_PERSON)
        assertEquals(HttpStatusCode.Conflict, post("/api/estimate/${estimate.identifier}/decide", json.encodeToString(decide)).status)
        assertEquals(EstimateStatus.PENDING, estimates(ticket.identifier).single().status)

        // Withdrawn by staff instead
        assertEquals(HttpStatusCode.OK, post("/api/estimate/${estimate.identifier}/withdraw", "").status)
        assertEquals(HttpStatusCode.Conflict, post("/api/estimate/${estimate.identifier}/withdraw", "").status)
        assertEquals(EstimateStatus.WITHDRAWN, estimates(ticket.identifier).single().status)
    }
}

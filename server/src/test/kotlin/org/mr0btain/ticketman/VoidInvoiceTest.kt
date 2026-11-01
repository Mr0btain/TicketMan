package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*
import kotlin.uuid.Uuid

/** Voiding never strands a ticket that's still open, or money an invoice took */
class VoidInvoiceTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val customer = Customer(name = "Ada Lovelace", phone = "5551234567")

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
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer)).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String = "", as_: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.checkIn(deposit: PaymentRequest? = null): CheckInResponse {
        val request = CheckInRequest(ticket = Ticket(device = Device("Apple", "iPhone 14"), problem = "Cracked"), customerIdentifier = customer.identifier, deposit = deposit)
        val response = post("/api/counter/check-in", json.encodeToString(request))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.invoiceFor(ticket: Uuid): Invoice {
        val response = post("/api/invoice/ticket/$ticket")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.void(invoice: Uuid) = post("/api/invoice/$invoice/void", as_ = adminToken)

    private suspend fun ApplicationTestBuilder.refund(invoice: Uuid, request: RefundRequest) =
        post("/api/invoice/$invoice/refund", json.encodeToString(request), adminToken)

    private suspend fun ApplicationTestBuilder.credit(): Long =
        json.decodeFromString<Customer>(client.get("/api/data/customer/get/${customer.identifier}") { bearerAuth(token) }.bodyAsText()).creditCents

    private fun labor() = Charge(type = ChargeType.LABOR, description = "Screen", quantity = 1.0, unitPriceCents = 5_000, taxable = false)

    @Test
    fun anOpenTicketGetsANewInvoiceAfterVoiding() = testApplication {
        setup()
        val ticket = checkIn().ticket.identifier
        val first = invoiceFor(ticket)
        assertEquals(HttpStatusCode.OK, void(first.identifier).status)

        val second = invoiceFor(ticket)
        assertNotEquals(first.identifier, second.identifier)
        assertEquals(InvoiceStatus.OPEN, second.status)
        assertNotEquals(first.number, second.number)

        // Asking again is the same one, and the void one stays on file
        assertEquals(second.identifier, invoiceFor(ticket).identifier)
        val kept = json.decodeFromString<Invoice>(client.get("/api/invoice/${first.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(InvoiceStatus.VOID, kept.status)

        // The ticket can be charged and picked up as usual
        assertEquals(HttpStatusCode.OK, post("/api/invoice/${second.identifier}/update", json.encodeToString(InvoiceChangeRequest(added = listOf(labor())))).status)
        val pickup = PickupRequest(ticket, listOf(PaymentRequest(PaymentMethod.CASH, 5_000)))
        val picked = post("/api/counter/pickup", json.encodeToString(pickup))
        assertEquals(HttpStatusCode.OK, picked.status, picked.bodyAsText())
        assertEquals(second.identifier, json.decodeFromString<Invoice>(picked.bodyAsText()).identifier)

        val closed = json.decodeFromString<Ticket>(client.get("/api/data/ticket/get/$ticket") { bearerAuth(token) }.bodyAsText())
        assertEquals(TicketStatusType.PICKED_UP, closed.status.status)
    }

    @Test
    fun aPickedUpTicketDoesntGetANewInvoice() = testApplication {
        setup()
        val ticket = checkIn().ticket.identifier
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket))).status)
        val paid = invoiceFor(ticket)
        assertEquals(HttpStatusCode.OK, void(paid.identifier).status)

        val after = invoiceFor(ticket)
        assertEquals(paid.identifier, after.identifier)
        assertEquals(InvoiceStatus.VOID, after.status)
    }

    @Test
    fun anOpenInvoiceHoldingPaymentsCantBeVoided() = testApplication {
        setup()
        val checkedIn = checkIn(PaymentRequest(PaymentMethod.CASH, 5_000))
        val invoice = checkedIn.invoice!!

        assertEquals(HttpStatusCode.Conflict, void(invoice.identifier).status)

        // Taking the deposit off first lets it through
        val payment = invoice.payments.single().identifier
        assertEquals(HttpStatusCode.OK, client.delete("/api/invoice/${invoice.identifier}/payments/$payment") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.OK, void(invoice.identifier).status)
    }

    @Test
    fun moneyOnAVoidInvoiceCanStillGoBack() = testApplication {
        setup()
        // A paid invoice voided afterwards, as a card payment landing on a void invoice would leave it too
        val ticket = checkIn().ticket.identifier
        val invoice = invoiceFor(ticket)
        post("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChangeRequest(added = listOf(labor()))))
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket, listOf(PaymentRequest(PaymentMethod.CASH, 5_000))))).status)
        assertEquals(HttpStatusCode.OK, void(invoice.identifier).status)

        // More than it holds is refused, as store credit it goes to the customer
        assertEquals(HttpStatusCode.BadRequest, refund(invoice.identifier, RefundRequest(PaymentMethod.STORE_CREDIT, 5_001, "Voided")).status)
        assertEquals(HttpStatusCode.OK, refund(invoice.identifier, RefundRequest(PaymentMethod.STORE_CREDIT, 5_000, "Voided")).status)
        assertEquals(5_000, credit())

        // And then there's nothing left
        assertEquals(HttpStatusCode.Conflict, refund(invoice.identifier, RefundRequest(PaymentMethod.CASH, 1, "Again")).status)
    }

    @Test
    fun anOpenInvoiceStillSaysToTakeThePaymentOff() = testApplication {
        setup()
        val invoice = checkIn(PaymentRequest(PaymentMethod.CASH, 5_000)).invoice!!
        val refused = refund(invoice.identifier, RefundRequest(PaymentMethod.CASH, 5_000, "No"))
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertContains(refused.bodyAsText(), "take the payment off")
    }

    @Test
    fun deletingATicketHandlesEveryInvoiceItHad() = testApplication {
        setup()
        val ticket = checkIn().ticket.identifier
        val first = invoiceFor(ticket)
        void(first.identifier)
        val second = invoiceFor(ticket)

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/data/ticket/remove/$ticket") { bearerAuth(adminToken) }.status)

        // The void one is kept, the open one with nothing paid goes with the ticket
        val kept = client.get("/api/invoice/${first.identifier}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, kept.status)
        assertTrue(json.decodeFromString<Invoice>(kept.bodyAsText()).ticketRemoved)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/invoice/${second.identifier}") { bearerAuth(token) }.status)
    }
}

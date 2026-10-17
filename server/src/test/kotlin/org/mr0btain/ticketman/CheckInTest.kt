package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Checking devices in at the counter
 */
class CheckInTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", name = "terry:tech", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)
    private val customer = Customer(name = "jane:doe", phone = "555-0100")

    private val signature = SignatureInput(
        strokes = listOf(listOf(SignaturePoint(0.1f, 0.5f), SignaturePoint(0.4f, 0.3f), SignaturePoint(0.9f, 0.6f))),
        aspectRatio = 3f,
        signedName = "Jane Doe"
    )

    private lateinit var token: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(terms: String = "") {
        configureTestServer(dataDir)
        startApplication()
        UserDatabaseImpl().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
        token = login(tech)

        post("/api/data/customer/add", json.encodeToString(customer))
        if (terms.isNotEmpty()) post("/api/shop/update", json.encodeToString(ShopProfile(repairTerms = terms)), login(admin))
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

    private fun request(
        ticket: Ticket = Ticket(device = Device("Apple", "iPhone 14", "SN1"), problem = "Cracked screen", accessories = "Case"),
        deposit: PaymentRequest? = null,
        signature: SignatureInput? = null,
        technician: User? = tech
    ) = CheckInRequest(ticket, customer.identifier, technician?.identifier, deposit, signature)

    private suspend fun ApplicationTestBuilder.checkIn(request: CheckInRequest, as_: String = token) =
        post("/api/counter/check-in", json.encodeToString(request), as_)

    @Test
    fun checkInMakesTheTicketWithEverythingAttached() = testApplication {
        setup()

        val response = checkIn(request(deposit = PaymentRequest(PaymentMethod.CARD, 5_000), signature = signature))
        assertEquals(HttpStatusCode.Created, response.status)

        val result = json.decodeFromString<CheckInResponse>(response.bodyAsText())
        val ticket = result.ticket
        assertNotNull(ticket.number)
        assertEquals("Cracked screen", ticket.problem)

        val linkedCustomer = json.decodeFromString<EntityReference>(client.get("/api/data/ticket/get/customer/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(customer.identifier, linkedCustomer.identifier)

        val linkedTech = json.decodeFromString<EntityReference>(client.get("/api/data/ticket/get/user/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(tech.identifier, linkedTech.identifier)

        // Deposit is on the invoice
        val invoice = result.invoice!!
        assertEquals(5_000, invoice.paidCents)
        assertEquals("Deposit", invoice.payments.single().note)
        assertEquals(invoice.identifier, json.decodeFromString<Invoice>(client.get("/api/invoice/ticket/${ticket.identifier}") { bearerAuth(token) }.bodyAsText()).identifier)

        val saved = json.decodeFromString<TicketSignature>(client.get("/api/data/ticket/signature/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals("Jane Doe", saved.signedName)
        assertEquals(tech.username, saved.takenBy)
        assertEquals(3, saved.strokes.single().size)

        val history = json.decodeFromString<List<TicketStatusChange>>(client.get("/api/data/ticket/get/history/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(TicketStatusType.IN_PROGRESS, history.single().status)
    }

    @Test
    fun noDepositMeansNoInvoiceYet() = testApplication {
        setup()

        val result = json.decodeFromString<CheckInResponse>(checkIn(request(technician = null)).bodyAsText())

        assertNull(result.invoice)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/data/ticket/get/user/${result.ticket.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun badCheckInsChangeNothing() = testApplication {
        setup()

        val blankDevice = request(ticket = Ticket(problem = "x"))
        val noProblem = request(ticket = Ticket(device = Device("Dell", "XPS")))
        val noCustomer = request().copy(customerIdentifier = clerk.identifier)
        val restrictedTech = request(technician = clerk)
        val freeDeposit = request(deposit = PaymentRequest(PaymentMethod.CASH, 0))
        val blankSignature = request(signature = signature.copy(strokes = emptyList()))
        val unnamedSignature = request(signature = signature.copy(signedName = " "))
        val offPad = request(signature = signature.copy(strokes = listOf(listOf(SignaturePoint(0f, 0f), SignaturePoint(2f, 0f)))))
        val alreadyDone = request(ticket = Ticket(device = Device("Dell", "XPS"), problem = "x", status = TicketStatus(TicketStatusType.DONE)))

        // Check in time comes from the identifier, a random (version 4) one would have none
        val randomIdentifier = request(ticket = Ticket(identifier = Uuid.random(), device = Device("Dell", "XPS"), problem = "x"))

        listOf(blankDevice, noProblem, noCustomer, restrictedTech, freeDeposit, blankSignature, unnamedSignature, offPad, alreadyDone, randomIdentifier).forEach {
            assertEquals(HttpStatusCode.BadRequest, checkIn(it).status, "Should refuse $it")
            assertNull(TicketDatabaseImpl().getById(it.ticket.identifier))
        }
    }

    @Test
    fun shopTermsNeedASignatureAndAreCopiedIn() = testApplication {
        setup(terms = "Not responsible for data loss.")

        assertEquals(HttpStatusCode.BadRequest, checkIn(request()).status)

        val result = json.decodeFromString<CheckInResponse>(checkIn(request(signature = signature)).bodyAsText())
        val saved = json.decodeFromString<TicketSignature>(client.get("/api/data/ticket/signature/${result.ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals("Not responsible for data loss.", saved.terms)
    }

    @Test
    fun sameTicketCantBeCheckedInTwice() = testApplication {
        setup()
        val once = request()

        assertEquals(HttpStatusCode.Created, checkIn(once).status)
        assertEquals(HttpStatusCode.Conflict, checkIn(once).status)
    }

    @Test
    fun restrictedUsersCantCheckIn() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Unauthorized, checkIn(request(), as_ = login(clerk)).status)
    }
}

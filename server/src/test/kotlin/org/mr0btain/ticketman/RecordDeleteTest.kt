package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*
import kotlin.uuid.Uuid

/** Normal staff clear up mistakes, the record of finished work or money taken is an admin's to remove */
class RecordDeleteTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private lateinit var token: String
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
        token = login(tech)
        adminToken = login(admin)
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.customer(): Uuid {
        val customer = Customer(name = "Ada")
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer)).status)
        return customer.identifier
    }

    private suspend fun ApplicationTestBuilder.checkIn(customer: Uuid, deposit: PaymentRequest? = null): Uuid {
        val request = CheckInRequest(ticket = Ticket(device = Device("Apple", "iPhone"), problem = "Cracked"), customerIdentifier = customer, deposit = deposit)
        return json.decodeFromString<CheckInResponse>(post("/api/counter/check-in", json.encodeToString(request)).bodyAsText()).ticket.identifier
    }

    private suspend fun ApplicationTestBuilder.delete(path: String, as_: String) = client.delete(path) { bearerAuth(as_) }

    @Test
    fun aCustomerWithAnOpenTicketCantBeDeletedByAnyone() = testApplication {
        setup()
        val customer = customer()
        val ticket = checkIn(customer)

        listOf(token, adminToken).forEach { caller ->
            val refused = delete("/api/data/customer/remove/$customer", caller)
            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertContains(refused.bodyAsText(), "open ticket")
        }

        // Closed, it has a history, which is an admin's to remove
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket))).status)
        assertEquals(HttpStatusCode.Forbidden, delete("/api/data/customer/remove/$customer", token).status)
        assertEquals(HttpStatusCode.Accepted, delete("/api/data/customer/remove/$customer", adminToken).status)
    }

    @Test
    fun aCustomerMadeByMistakeCanBeDeleted() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Accepted, delete("/api/data/customer/remove/${customer()}", token).status)
        assertEquals(HttpStatusCode.NotFound, delete("/api/data/customer/remove/${Uuid.random()}", token).status)
    }

    @Test
    fun aTicketWithMoneyOrFinishedIsAnAdminsToDelete() = testApplication {
        setup()
        val customer = customer()

        val withDeposit = checkIn(customer, PaymentRequest(PaymentMethod.CASH, 1_000))
        assertEquals(HttpStatusCode.Forbidden, delete("/api/data/ticket/remove/$withDeposit", token).status)
        assertEquals(HttpStatusCode.Accepted, delete("/api/data/ticket/remove/$withDeposit", adminToken).status)

        val finished = checkIn(customer)
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(finished))).status)
        assertEquals(HttpStatusCode.Forbidden, delete("/api/data/ticket/remove/$finished", token).status)
        assertEquals(HttpStatusCode.Accepted, delete("/api/data/ticket/remove/$finished", adminToken).status)
    }

    @Test
    fun aTicketMadeByMistakeCanBeDeleted() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Accepted, delete("/api/data/ticket/remove/${checkIn(customer())}", token).status)
    }
}

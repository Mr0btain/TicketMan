package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.ChecklistItem
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*

/** Saving a copy of a record someone else has saved since is refused, rather than undoing what they did */
class RevisionTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private lateinit var token: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().add(tech)
        token = json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(tech.username, tech.password)))
            }.bodyAsText()
        ).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend inline fun <reified T> ApplicationTestBuilder.read(path: String): T =
        json.decodeFromString(client.get(path) { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.checkedIn(): Ticket {
        val customer = Customer(name = "Ada")
        post("/api/data/customer/add", json.encodeToString(customer))
        val response = post("/api/counter/check-in", json.encodeToString(CheckInRequest(
            ticket = Ticket(device = Device("Apple", "iPhone"), problem = "Cracked", checklist = listOf(ChecklistItem("Powers on"))),
            customerIdentifier = customer.identifier
        )))
        return json.decodeFromString<CheckInResponse>(response.bodyAsText()).ticket
    }

    @Test
    fun theSecondSaveFromTheSameCopyOfATicketIsRefused() = testApplication {
        setup()
        val opened = checkedIn()
        assertEquals(0, opened.revision)

        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(opened.copy(problem = "From A"))).status)

        val second = post("/api/data/ticket/update", json.encodeToString(opened.copy(accessories = "From B")))
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertContains(second.bodyAsText(), "changed this ticket")

        val saved = read<Ticket>("/api/data/ticket/get/${opened.identifier}")
        assertEquals("From A", saved.problem, "What was saved first has to survive")
        assertEquals("", saved.accessories)
        assertEquals(1, saved.revision)

        // Opening it again and saving works
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(saved.copy(accessories = "From B"))).status)
        assertEquals("From B", read<Ticket>("/api/data/ticket/get/${opened.identifier}").accessories)
    }

    @Test
    fun whatTheServerChangesCountsToo() = testApplication {
        setup()
        val opened = checkedIn()
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(opened.identifier))).status)

        // Saved from before the pickup: its status was about to go back to what it was
        assertEquals(HttpStatusCode.Conflict, post("/api/data/ticket/update", json.encodeToString(opened.copy(problem = "Late"))).status)
    }

    @Test
    fun anUpdateWithoutARevisionIsRefused() = testApplication {
        setup()
        val opened = checkedIn()

        assertEquals(HttpStatusCode.BadRequest, post("/api/data/ticket/update", json.encodeToString(opened.copy(problem = "No revision", revision = null))).status)
        assertEquals(opened.problem, read<Ticket>("/api/data/ticket/get/${opened.identifier}").problem)
    }

    @Test
    fun aTicketThatsGoneIsStillNotFound() = testApplication {
        setup()
        assertEquals(HttpStatusCode.NotFound, post("/api/data/ticket/update", json.encodeToString(Ticket(revision = 0, status = TicketStatus(TicketStatusType.IN_PROGRESS)))).status)
    }

    @Test
    fun customersAreCheckedTheSameWay() = testApplication {
        setup()
        val customer = Customer(name = "Ada")
        post("/api/data/customer/add", json.encodeToString(customer))
        val opened = read<Customer>("/api/data/customer/get/${customer.identifier}")

        assertEquals(HttpStatusCode.Accepted, post("/api/data/customer/update", json.encodeToString(opened.copy(phone = "5551112222"))).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/data/customer/update", json.encodeToString(opened.copy(email = "a@b.test"))).status)

        val saved = read<Customer>("/api/data/customer/get/${customer.identifier}")
        assertEquals("5551112222", saved.phone)
        assertEquals("", saved.email)
    }

    @Test
    fun inventoryIsCheckedTheSameWayButStockDoesntGetInTheWay() = testApplication {
        setup()
        val item = Inventory(name = "Screen", stock = 5, priceCents = 1_000)
        post("/api/data/inventory/add", json.encodeToString(item))
        val opened = read<Inventory>("/api/data/inventory/get/${item.identifier}")

        // A pickup or a count isn't part of what an update saves
        assertEquals(HttpStatusCode.Accepted, post("/api/data/inventory/set/stock/${item.identifier}", """{"from":5,"to":4}""").status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/inventory/update", json.encodeToString(opened.copy(priceCents = 2_000))).status)

        assertEquals(HttpStatusCode.Conflict, post("/api/data/inventory/update", json.encodeToString(opened.copy(name = "Old copy"))).status)
        val saved = read<Inventory>("/api/data/inventory/get/${item.identifier}")
        assertEquals(2_000, saved.priceCents)
        assertEquals("Screen", saved.name)
        assertEquals(4, saved.stock)
    }
}

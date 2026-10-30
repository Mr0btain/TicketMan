package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.RepeatCheckRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Ticket/customer links, notes, the counter's search, queue and repeat check, and read only access
 */
@OptIn(ExperimentalUuidApi::class)
class CounterTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", name = "Terry Tech", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var token: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(viewer)
        }
        token = login(tech)
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

    private suspend fun ApplicationTestBuilder.get(path: String, as_: String = token) =
        client.get(path) { bearerAuth(as_) }

    private suspend fun ApplicationTestBuilder.addTicket(ticket: Ticket = Ticket()): Ticket =
        json.decodeFromString(post("/api/data/ticket/add", json.encodeToString(ticket)).bodyAsText())

    private suspend fun ApplicationTestBuilder.addCustomer(customer: Customer): Customer {
        post("/api/data/customer/add", json.encodeToString(customer))
        return customer
    }

    private suspend fun ApplicationTestBuilder.link(ticket: Ticket, customer: Customer) {
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/customer/${ticket.identifier}", json.encodeToString(customer.identifier)).status)
    }

    // ------------------------------------------ Links and notes ------------------------------------------

    @Test
    fun ticketsLinkToOneCustomerAndOneTech() = testApplication {
        setup()
        val jane = addCustomer(Customer(name = "Jane Doe"))
        val john = addCustomer(Customer(name = "John Roe"))
        val ticket = addTicket()

        link(ticket, jane)
        // Linking again moves it rather than adding a second customer
        link(ticket, john)

        val customer = json.decodeFromString<EntityReference>(get("/api/data/ticket/get/customer/${ticket.identifier}").bodyAsText())
        assertEquals(john.identifier, customer.identifier)

        val janesTickets = json.decodeFromString<List<Ticket>>(get("/api/data/customer/get/tickets/${jane.identifier}").bodyAsText())
        assertTrue(janesTickets.isEmpty())

        // Customer route can link too
        post("/api/data/customer/add/ticket/${jane.identifier}", json.encodeToString(ticket.identifier))
        val janesNow = json.decodeFromString<List<Ticket>>(get("/api/data/customer/get/tickets/${jane.identifier}").bodyAsText())
        assertEquals(listOf(ticket.identifier), janesNow.map { it.identifier })

        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/user/${ticket.identifier}", json.encodeToString(tech.identifier)).status)
        val user = json.decodeFromString<EntityReference>(get("/api/data/ticket/get/user/${ticket.identifier}").bodyAsText())
        assertEquals(tech.identifier, user.identifier)

        assertEquals(HttpStatusCode.BadRequest, post("/api/data/ticket/set/user/${ticket.identifier}", json.encodeToString(Uuid.random())).status)
    }

    @Test
    fun onlyActiveTechniciansWithNormalAccessTakeTickets() = testApplication {
        setup()
        val ticket = addTicket()
        val gone = User(username = "gone", password = "gone_pass", accessLevel = AccessLevel.NORMAL, status = UserStatus.DEACTIVATED)
        UserDatabase().add(gone)

        assertEquals(HttpStatusCode.BadRequest, post("/api/data/ticket/set/user/${ticket.identifier}", json.encodeToString(viewer.identifier)).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/data/ticket/set/user/${ticket.identifier}", json.encodeToString(gone.identifier)).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/data/ticket/set/user/${Uuid.random()}", json.encodeToString(tech.identifier)).status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/user/${ticket.identifier}", json.encodeToString(admin.identifier)).status)
    }

    @Test
    fun notesCanBeAddedReadAndRemoved() = testApplication {
        setup()
        val ticket = addTicket()
        val customer = addCustomer(Customer(name = "Jane Doe"))

        listOf("ticket" to ticket.identifier, "customer" to customer.identifier).forEach { (type, identifier) ->
            val note = Note(message = "Called about $type")

            assertEquals(HttpStatusCode.Created, post("/api/data/$type/add/note/$identifier", json.encodeToString(note)).status)

            val notes = json.decodeFromString<List<Note>>(get("/api/data/$type/get/notes/$identifier").bodyAsText())
            assertEquals(listOf(note.message), notes.map { it.message })

            // Blank authors get the writer's username
            assertEquals(tech.username, notes.single().author)

            client.delete("/api/data/$type/remove/note/${note.identifier}") { bearerAuth(token) }
            val after = json.decodeFromString<List<Note>>(get("/api/data/$type/get/notes/$identifier").bodyAsText())
            assertTrue(after.isEmpty(), "$type note wasn't removed")
        }
    }

    // ------------------------------------------ Access ------------------------------------------

    @Test
    fun restrictedUsersCanViewButNotChange() = testApplication {
        setup()
        val ticket = addTicket()
        val viewerToken = login(viewer)
        val page = "?offset=0&limit=10"

        listOf("ticket", "customer", "inventory").forEach { type ->
            assertEquals(HttpStatusCode.OK, get("/api/data/$type/fetch$page", viewerToken).status, "$type fetch")
        }
        assertEquals(HttpStatusCode.OK, get("/api/data/ticket/get/${ticket.identifier}", viewerToken).status)
        assertEquals(HttpStatusCode.OK, get("/api/data/ticket/get/history/${ticket.identifier}", viewerToken).status)
        assertEquals(HttpStatusCode.OK, get("/api/counter/queue", viewerToken).status)
        assertEquals(HttpStatusCode.OK, get("/api/counter/search?query=1&limit=10", viewerToken).status)

        // Users too, like every other record type, with nothing secret in them
        val users = get("/api/user/fetch$page", viewerToken)
        assertEquals(HttpStatusCode.OK, users.status)
        assertTrue(json.decodeFromString<List<User>>(users.bodyAsText()).all { it.password.isEmpty() })
        assertEquals(HttpStatusCode.OK, get("/api/user/search?query=v", viewerToken).status)

        assertEquals(HttpStatusCode.Forbidden, post("/api/data/ticket/add", json.encodeToString(Ticket()), viewerToken).status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/data/ticket/update", json.encodeToString(ticket), viewerToken).status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/data/customer/add", json.encodeToString(Customer()), viewerToken).status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/data/ticket/add/note/${ticket.identifier}", json.encodeToString(Note()), viewerToken).status)
    }

    // ------------------------------------------ Search ------------------------------------------

    @Test
    fun customersAreFoundByFullNameAndPhoneDigits() = testApplication {
        setup()
        val jane = addCustomer(Customer(name = "Jane Doe", phone = "(555) 123-4567"))
        addCustomer(Customer(name = "John Roe", phone = "555-999-0000"))

        suspend fun search(query: String) =
            json.decodeFromString<List<TmSearchResult>>(client.get("/api/data/customer/search") { bearerAuth(token); parameter("query", query); parameter("limit", 10) }.bodyAsText())
                .map { it.identifier }

        assertEquals(listOf(jane.identifier), search("jane doe"))
        assertEquals(listOf(jane.identifier), search("doe"))
        assertEquals(listOf(jane.identifier), search("5551234567"))
        assertEquals(listOf(jane.identifier), search("123-45"))
        assertEquals(2, search("555").size)
    }

    @Test
    fun counterSearchBringsUpACustomersTickets() = testApplication {
        setup()
        val jane = addCustomer(Customer(name = "Jane Doe", phone = "555-123-4567"))
        val first = addTicket(Ticket(device = Device("Apple", "iPhone 14")))
        val second = addTicket(Ticket(device = Device("Dell", "XPS 13")))
        link(first, jane)
        link(second, jane)

        val result = json.decodeFromString<CounterSearchResponse>(get("/api/counter/search?query=5551234567&limit=10").bodyAsText())

        assertEquals(listOf(jane.identifier), result.customers.map { it.identifier })
        assertEquals(setOf(first.identifier, second.identifier), result.tickets.map { it.ticket.identifier }.toSet())
        assertTrue(result.tickets.all { it.customer?.identifier == jane.identifier })

        // Ticket numbers work too
        val byNumber = json.decodeFromString<CounterSearchResponse>(get("/api/counter/search?query=${second.number}&limit=10").bodyAsText())
        assertEquals(second.identifier, byNumber.tickets.first().ticket.identifier)
    }

    // ------------------------------------------ Queue ------------------------------------------

    @Test
    fun queueSortsTicketsIntoTheRightLists() = testApplication {
        setup()
        val now = Clock.System.now().toEpochMilliseconds()

        val ready = addTicket(Ticket(status = TicketStatus(TicketStatusType.DONE)))
        val dueToday = addTicket(Ticket(promisedAt = now))
        val overdue = addTicket(Ticket(promisedAt = now - 2.days.inWholeMilliseconds))
        val overdueButDone = addTicket(Ticket(status = TicketStatus(TicketStatusType.DONE), promisedAt = now - 2.days.inWholeMilliseconds))
        val later = addTicket(Ticket(promisedAt = now + 3.days.inWholeMilliseconds))
        // Only the counter can close a ticket
        val pickedUp = addTicket()
        assertEquals(HttpStatusCode.OK, post("/api/counter/pickup", json.encodeToString(PickupRequest(pickedUp.identifier))).status)

        val queue = json.decodeFromString<CounterQueueResponse>(get("/api/counter/queue?recent=3").bodyAsText())

        assertEquals(setOf(ready.identifier, overdueButDone.identifier), queue.readyForPickup.map { it.ticket.identifier }.toSet())
        assertEquals(listOf(dueToday.identifier), queue.dueToday.map { it.ticket.identifier })
        assertEquals(listOf(overdue.identifier), queue.overdue.map { it.ticket.identifier })
        assertEquals(3, queue.recentlyCheckedIn.size)

        val everything = (queue.readyForPickup + queue.dueToday + queue.overdue + queue.recentlyCheckedIn).map { it.ticket.identifier }
        assertFalse(pickedUp.identifier in everything, "Picked up tickets shouldn't be in the queue")
        assertFalse(later.identifier in queue.dueToday.map { it.ticket.identifier })
    }

    // ------------------------------------------ Repeat check ------------------------------------------

    @Test
    fun repeatCheckMatchesSerialOrModelWithinTheWindow() = testApplication {
        setup()
        val jane = addCustomer(Customer(name = "Jane Doe"))
        val phone = addTicket(Ticket(device = Device("Apple", "iPhone 14", "SN1")))
        val laptop = addTicket(Ticket(device = Device("Dell", "XPS 13")))
        link(phone, jane)
        link(laptop, jane)

        suspend fun check(device: Device, exclude: Uuid? = null) =
            json.decodeFromString<List<TicketSummary>>(
                post("/api/counter/repeat-check", json.encodeToString(RepeatCheckRequest(jane.identifier, device, exclude))).bodyAsText()
            ).map { it.ticket.identifier }

        assertEquals(listOf(phone.identifier), check(Device("apple", "iphone 14", "sn1")))
        assertTrue(check(Device("Apple", "iPhone 14", "SN2")).isEmpty(), "Different serial shouldn't match")
        assertEquals(listOf(laptop.identifier), check(Device("Dell", "XPS 13")))
        assertTrue(check(Device("Apple", "iPhone 14", "SN1"), exclude = phone.identifier).isEmpty())

        // Picked up long ago is outside the default 90 days
        transaction { TicketEntity.findById(phone.identifier)!!.pickedUpAt = Clock.System.now().toEpochMilliseconds() - 120.days.inWholeMilliseconds }
        assertTrue(check(Device("Apple", "iPhone 14", "SN1")).isEmpty())

        // Admins can widen the window
        val adminToken = login(admin)
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(ShopProfile(repeatWindowDays = 365)), adminToken).status)
        assertEquals(listOf(phone.identifier), check(Device("Apple", "iPhone 14", "SN1")))

        assertEquals(HttpStatusCode.BadRequest, post("/api/shop/update", json.encodeToString(ShopProfile(repeatWindowDays = 0)), adminToken).status)
    }
}

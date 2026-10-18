package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Inventory routes end to end, and the record routes older tests didn't reach
 */
class InventoryTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)

    private val screen = Inventory(name = "iPhone 14 Screen", description = "OEM panel", stock = 5, priceCents = 12_000, costCents = 6_000)
    private val battery = Inventory(name = "Pixel 7 Battery", stock = 2, priceCents = 5_000, costCents = 2_000)
    private val charger = Inventory(name = "USB-C Charger", stock = 9, priceCents = 2_500, costCents = 800)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(tech)
            add(viewer)
        }
    }

    private suspend fun ApplicationTestBuilder.token(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.add(item: Inventory, token: String) =
        assertEquals(HttpStatusCode.Created, post("/api/data/inventory/add", json.encodeToString(item), token).status)

    private suspend fun ApplicationTestBuilder.get(identifier: Uuid, token: String) =
        post("/api/data/inventory/get", json.encodeToString(identifier), token)

    @Test
    fun itemsCanBeAddedReadAndChanged() = testApplication {
        setup()
        val token = token(tech)
        add(screen, token)

        assertEquals(screen, json.decodeFromString<Inventory>(get(screen.identifier, token).bodyAsText()))

        val restocked = screen.copy(stock = 12, priceCents = 11_500)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/inventory/update", json.encodeToString(restocked), token).status)
        assertEquals(restocked, json.decodeFromString<Inventory>(get(screen.identifier, token).bodyAsText()))
    }

    @Test
    fun missingItemsAreNotFound() = testApplication {
        setup()
        val token = token(tech)

        assertEquals(HttpStatusCode.NotFound, get(Uuid.random(), token).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/data/inventory/update", json.encodeToString(screen), token).status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/data/inventory/remove/${Uuid.random()}") { bearerAuth(token) }.status)
    }

    @Test
    fun theListIsPagedByNameIgnoringCase() = testApplication {
        setup()
        val token = token(tech)
        listOf(charger, screen, battery).forEach { add(it, token) }

        val first = json.decodeFromString<List<Inventory>>(post("/api/data/inventory/fetch", json.encodeToString(PagedRequest(0, 2)), token).bodyAsText())
        val rest = json.decodeFromString<List<Inventory>>(post("/api/data/inventory/fetch", json.encodeToString(PagedRequest(2, 2)), token).bodyAsText())

        assertEquals(listOf(screen, battery, charger).map { it.name }, (first + rest).map { it.name })
        assertEquals(2, first.size)
    }

    @Test
    fun searchMatchesNameAndDescription() = testApplication {
        setup()
        val token = token(tech)
        listOf(screen, battery, charger).forEach { add(it, token) }

        fun ids(results: String) = json.decodeFromString<List<SearchResult>>(results).map { it.identifier }

        assertEquals(listOf(battery.identifier), ids(post("/api/data/inventory/search", json.encodeToString(SearchQuery("pixel", 10)), token).bodyAsText()))
        assertEquals(listOf(screen.identifier), ids(post("/api/data/inventory/search", json.encodeToString(SearchQuery("oem", 10)), token).bodyAsText()))
        assertTrue(ids(post("/api/data/inventory/search", json.encodeToString(SearchQuery("tablet", 10)), token).bodyAsText()).isEmpty())
    }

    @Test
    fun notesGoOnAnItem() = testApplication {
        setup()
        val token = token(tech)
        add(screen, token)
        val note = Note(message = "Supplier backordered")

        assertEquals(HttpStatusCode.Created, post("/api/data/inventory/add/note/${screen.identifier}", json.encodeToString(note), token).status)
        val saved = json.decodeFromString<List<Note>>(post("/api/data/inventory/get/notes/${screen.identifier}", "\"${screen.identifier}\"", token).bodyAsText())

        assertEquals(listOf("Supplier backordered"), saved.map { it.message })
        // Signed by whoever wrote it
        assertEquals("tech", saved.single().author)
    }

    @Test
    fun removingAnItemTakesItAway() = testApplication {
        setup()
        val token = token(tech)
        add(screen, token)

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/data/inventory/remove/${screen.identifier}") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, get(screen.identifier, token).status)
    }

    @Test
    fun restrictedUsersCanLookButNotChange() = testApplication {
        setup()
        add(screen, token(tech))
        val token = token(viewer)

        assertEquals(HttpStatusCode.OK, get(screen.identifier, token).status)
        assertEquals(HttpStatusCode.OK, post("/api/data/inventory/fetch", json.encodeToString(PagedRequest(0, 10)), token).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/api/data/inventory/add", json.encodeToString(battery), token).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/api/data/inventory/update", json.encodeToString(screen.copy(stock = 0)), token).status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/data/inventory/remove/${screen.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun customersCanBeUpdated() = testApplication {
        setup()
        val token = token(tech)
        val customer = Customer(name = "Jane:Doe", phone = "5550100")
        assertEquals(HttpStatusCode.Accepted, post("/api/data/customer/add", json.encodeToString(customer), token).status)

        val moved = customer.copy(phone = "5550199", email = "jane@example.com")
        assertEquals(HttpStatusCode.Accepted, post("/api/data/customer/update", json.encodeToString(moved), token).status)
        // Names are kept lowercase and capitalised for display
        assertEquals(moved.copy(name = "jane:doe"), json.decodeFromString<Customer>(post("/api/data/customer/get", json.encodeToString(customer.identifier), token).bodyAsText()))

        assertEquals(HttpStatusCode.NotFound, post("/api/data/customer/update", json.encodeToString(Customer(name = "Nobody")), token).status)
    }
}

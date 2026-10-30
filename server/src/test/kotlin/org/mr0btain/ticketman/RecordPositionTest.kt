package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RecordPosition
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A record's position has to be where `/fetch` really puts it, or a list jumps to the wrong page
 */
@OptIn(ExperimentalUuidApi::class)
class RecordPositionTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.token(): String {
        startApplication()
        val admin = User(username = "owner", password = "owner_pass", accessLevel = AccessLevel.ADMIN)
        UserDatabase().add(admin)

        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(admin.username, admin.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.positionOf(token: String, route: String, identifier: Uuid): Long =
        client.get("$route/position/$identifier") { bearerAuth(token) }.let {
            assertEquals(HttpStatusCode.OK, it.status)
            json.decodeFromString<RecordPosition>(it.bodyAsText()).index
        }

    @Test
    fun customersAreWhereFetchPutsThem() = testApplication {
        configureTestServer(dataDir)
        val token = token()

        // Out of order, with a repeated name so the id has to settle it
        listOf("Mara", "Ann", "Zed", "Ann", "bob", "Ann").forEach { name ->
            client.post("/api/data/customer/add") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(Customer(name = name)))
            }
        }

        val listed = json.decodeFromString<List<Customer>>(client.get("/api/data/customer/fetch?limit=50") { bearerAuth(token) }.bodyAsText())
        assertEquals(6, listed.size)
        listed.forEachIndexed { index, customer ->
            assertEquals(index.toLong(), positionOf(token, "/api/data/customer", customer.identifier), customer.name)
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/data/customer/position/${Uuid.generateV7()}") { bearerAuth(token) }.status)
    }

    @Test
    fun itemsAreWhereFetchPutsThem() = testApplication {
        configureTestServer(dataDir)
        val token = token()

        listOf("washer", "Bolt", "bolt", "Anvil", "Écrou").forEach { name ->
            client.post("/api/data/inventory/add") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(Inventory(name = name)))
            }
        }

        val listed = json.decodeFromString<List<Inventory>>(client.get("/api/data/inventory/fetch?limit=50") { bearerAuth(token) }.bodyAsText())
        assertEquals(5, listed.size)
        listed.forEachIndexed { index, item ->
            assertEquals(index.toLong(), positionOf(token, "/api/data/inventory", item.identifier), item.name)
        }
    }

    @Test
    fun ticketsAreWhereFetchPutsThemAndOnlyYoursAreInMine() = testApplication {
        configureTestServer(dataDir)
        val token = token()

        repeat(5) {
            client.post("/api/data/ticket/add") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(Ticket()))
            }
        }

        val listed = json.decodeFromString<List<Ticket>>(client.get("/api/data/ticket/fetch?limit=50") { bearerAuth(token) }.bodyAsText())
        assertEquals(5, listed.size)
        listed.forEachIndexed { index, ticket ->
            assertEquals(index.toLong(), positionOf(token, "/api/data/ticket", ticket.identifier))
        }

        // Nothing's assigned to the admin
        val mine = client.get("/api/data/ticket/position/mine/${listed.first().identifier}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.NotFound, mine.status)
    }
}

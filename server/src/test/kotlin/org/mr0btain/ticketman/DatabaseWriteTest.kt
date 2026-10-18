package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Database writes say whether they happened, and routes pass that on
 */
@OptIn(ExperimentalUuidApi::class)
class DatabaseWriteTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(): String {
        configureTestServer(dataDir)
        startApplication()
        UserDatabaseImpl().add(tech)

        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(tech.username, tech.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    @Test
    fun writesToMissingItemsReturnFalse() = testApplication {
        setup()
        val customers = CustomerDatabaseImpl()
        val missing = Uuid.generateV7()

        assertFalse(customers.update(Customer(identifier = missing, name = "nobody")))
        assertFalse(customers.remove(missing))
        assertFalse(customers.addNote(missing, Note(author = "tech", message = "hello")))
        assertFalse(customers.removeNote(missing))
    }

    @Test
    fun addingTheSameItemTwiceFails() = testApplication {
        setup()
        val customers = CustomerDatabaseImpl()
        val customer = Customer(name = "jane:doe")

        assertTrue(customers.add(customer))
        assertFalse(customers.add(customer))
    }

    @Test
    fun aThrowInsideAnOuterTransactionRollsAllOfItBack() = testApplication {
        setup()
        val customers = CustomerDatabaseImpl()
        val first = Customer(name = "first:one")

        assertTrue(customers.add(first))

        // The second customer reuses the first one's id, so its insert throws and the new one goes with it
        val fresh = Customer(name = "fresh:one")
        assertFails {
            transaction {
                customers.add(fresh)
                customers.add(first.copy(name = "again:one"))
            }
        }

        assertNull(customers.getById(fresh.identifier))
        assertEquals("first:one", customers.getById(first.identifier)?.name)
    }

    @Test
    fun removingAMissingCustomerIsNotFound() = testApplication {
        val token = setup()

        val response = client.delete("/api/data/customer/remove/${Uuid.generateV7()}") { bearerAuth(token) }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun addingADuplicateCustomerIsAnError() = testApplication {
        val token = setup()
        val customer = Customer(name = "jane:doe")

        suspend fun add() = client.post("/api/data/customer/add") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(json.encodeToString(customer))
        }

        assertEquals(HttpStatusCode.Accepted, add().status)
        assertEquals(HttpStatusCode.InternalServerError, add().status)
    }
}

package org.mr0btain.ticketman

import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.API_VERSION_HEADER
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import org.mr0btain.ticketman.data.customer.Customer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.routing.BAD_REQUEST_MESSAGE
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

class ApplicationTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun testRoot() = testApplication {
        configureTestServer(dataDir)

        // Root route is a health check that just responds OK
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun pingSaysWhichApiTheServerSpeaks() = testApplication {
        configureTestServer(dataDir)

        val response = client.get(SERVER_PING_PATH)
        assertEquals(SERVER_PING_BODY, response.bodyAsText())
        assertEquals(API_VERSION.toString(), response.headers[API_VERSION_HEADER])
    }

    @Test
    fun aBodyThatCantBeReadIsABadRequest() = testApplication {
        configureTestServer(dataDir)
        startApplication()
        val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
        UserDatabase().add(tech)

        suspend fun login(body: String) = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        // Not JSON, a field the server doesn't have, and one missing
        listOf("{bad", """{"username":"tech","password":"tech_pass","extra":1}""", """{"username":"tech"}""").forEach { body ->
            val response = login(body)
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            // A reason for people, not the exception
            assertEquals(BAD_REQUEST_MESSAGE, response.bodyAsText(), body)
        }

        // An id that isn't a UUID, behind a login
        val token = json.decodeFromString<AuthResponse>(login(json.encodeToString(LoginRequest("tech", "tech_pass"))).bodyAsText()).accessToken!!
        val badBody = client.post("/api/data/customer/add/ticket/${Customer().identifier}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("\"not-an-id\"")
        }
        assertEquals(HttpStatusCode.BadRequest, badBody.status)

        // Paging that isn't a number
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/data/customer/fetch?offset=x") { bearerAuth(token) }.status)
    }

    @Test
    fun templateLeftoversAreGone() = testApplication {
        configureTestServer(dataDir)

        assertEquals(HttpStatusCode.NotFound, client.get("/json/kotlinx-serialization").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/static/index.html").status)
    }
}

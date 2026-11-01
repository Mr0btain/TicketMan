package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.PinCheck
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class HardeningTest {
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

    @Test
    fun guessesAtOnceStillOnlyGetTheAllowedTries() = testApplication {
        setup()
        val users = UserDatabase()
        assertTrue(users.setPin(tech.identifier, "482915"))

        val pool = Executors.newFixedThreadPool(32)
        val answers = try {
            pool.invokeAll((0 until 64).map { n -> Callable { users.checkPin(tech.identifier, "${100000 + n}") } }).map { it.get() }
        } finally {
            pool.shutdown()
        }

        assertTrue(answers.count { it == PinCheck.WRONG } < PinRules.MAX_ATTEMPTS, "Too many were checked: $answers")
        assertTrue(users.getById(tech.identifier)!!.pinLocked)

        // Locked, the right one doesn't get in either
        assertEquals(PinCheck.LOCKED, users.checkPin(tech.identifier, "482915"))
    }

    @Test
    fun theRightPinStartsTheCountOver() = testApplication {
        setup()
        val users = UserDatabase()
        users.setPin(tech.identifier, "482915")

        repeat(PinRules.MAX_ATTEMPTS - 1) { assertEquals(PinCheck.WRONG, users.checkPin(tech.identifier, "111111")) }
        assertEquals(PinCheck.OK, users.checkPin(tech.identifier, "482915"))
        assertEquals(0, users.getPinFailedAttempts(tech.identifier))

        repeat(PinRules.MAX_ATTEMPTS - 1) { assertEquals(PinCheck.WRONG, users.checkPin(tech.identifier, "111111")) }
        assertEquals(PinCheck.LOCKED, users.checkPin(tech.identifier, "111111"))
    }

    @Test
    fun aNotesAuthorIsWhoeverIsLoggedIn() = testApplication {
        setup()
        val ticket = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

        val note = Note(message = "Fixed it", author = "boss")
        val added = post("/api/data/ticket/add/note/${ticket.identifier}", json.encodeToString(note))
        assertEquals(HttpStatusCode.Created, added.status)
        assertEquals("tech", json.decodeFromString<Note>(added.bodyAsText()).author)

        // Nor can editing it change who wrote it
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update/note/${note.identifier}", json.encodeToString(note.copy(message = "Edited"))).status)
        val saved = json.decodeFromString<List<Note>>(client.get("/api/data/ticket/get/notes/${ticket.identifier}") { bearerAuth(token) }.bodyAsText()).single()
        assertEquals("Edited", saved.message)
        assertEquals("tech", saved.author)
    }

    @Test
    fun theWebDevServerCanSendTheLocationHeader() = testApplication {
        setup()

        val preflight = client.options("/api/data/ticket/fetch") {
            header(HttpHeaders.Origin, "http://localhost:8081")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,x-ticketman-location,x-ticketman-version")
        }

        assertEquals(HttpStatusCode.OK, preflight.status)
    }

    @Test
    fun responsesCarrySecurityHeaders() = testApplication {
        setup()

        val api = client.get("/api/ping")
        assertEquals("nosniff", api.headers["X-Content-Type-Options"])
        assertEquals("DENY", api.headers["X-Frame-Options"])
        assertEquals("no-referrer", api.headers["Referrer-Policy"])
        assertContains(api.headers["Content-Security-Policy"].orEmpty(), "frame-ancestors 'none'")

        // The status page's stricter ones are the only ones it has
        val status = client.get("/status")
        assertEquals(1, status.headers.getAll("Content-Security-Policy")?.size)
        assertContains(status.headers["Content-Security-Policy"].orEmpty(), "default-src 'none'")
    }
}

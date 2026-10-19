package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

/**
 * Staff notes are only for admins, and a note can only be changed through the routes of the record it's on
 */
class StaffNoteTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val boss = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(boss)
            add(tech)
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

    private suspend fun ApplicationTestBuilder.staffNotes(token: String) =
        client.get("/api/user/get/notes/${tech.identifier}") { bearerAuth(token) }

    @Test
    fun adminsKeepNotesOnStaff() = testApplication {
        setup()
        val token = token(boss)
        val note = Note(message = "Trained on Apple board repair")

        assertEquals(HttpStatusCode.Created, post("/api/user/add/note/${tech.identifier}", json.encodeToString(note), token).status)

        val saved = json.decodeFromString<List<Note>>(staffNotes(token).bodyAsText()).single()
        assertEquals("Trained on Apple board repair", saved.message)
        assertEquals("boss", saved.author)

        val edited = saved.copy(message = "Trained on Apple and Samsung board repair")
        assertEquals(HttpStatusCode.Accepted, post("/api/user/update/note/${note.identifier}", json.encodeToString(edited), token).status)
        assertEquals(listOf(edited.message), json.decodeFromString<List<Note>>(staffNotes(token).bodyAsText()).map { it.message })
    }

    @Test
    fun staffCantReadOrWriteStaffNotes() = testApplication {
        setup()
        val token = token(tech)

        assertEquals(HttpStatusCode.Forbidden, staffNotes(token).status)
        assertEquals(
            HttpStatusCode.Forbidden,
            post("/api/user/add/note/${tech.identifier}", json.encodeToString(Note(message = "Promote me")), token).status
        )
    }

    @Test
    fun aStaffNoteCantBeChangedThroughAnotherRecordsRoutes() = testApplication {
        setup()
        val note = Note(message = "Late twice this month")
        post("/api/user/add/note/${tech.identifier}", json.encodeToString(note), token(boss))
        val token = token(tech)

        assertEquals(
            HttpStatusCode.NotFound,
            post("/api/data/customer/update/note/${note.identifier}", json.encodeToString(note.copy(message = "Never late")), token).status
        )
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/data/ticket/remove/note/${note.identifier}") { bearerAuth(token) }.status)

        assertEquals(listOf(note.message), json.decodeFromString<List<Note>>(staffNotes(token(boss)).bodyAsText()).map { it.message })
    }

    @Test
    fun aCustomerNoteCanOnlyBeChangedAsACustomerNote() = testApplication {
        setup()
        val token = token(tech)
        val customer = Customer(name = "Jane:Doe")
        post("/api/data/customer/add", json.encodeToString(customer), token)
        val note = Note(message = "Prefers texts")
        post("/api/data/customer/add/note/${customer.identifier}", json.encodeToString(note), token)

        val edited = json.encodeToString(note.copy(message = "Prefers calls"))
        assertEquals(HttpStatusCode.NotFound, post("/api/data/inventory/update/note/${note.identifier}", edited, token).status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/customer/update/note/${note.identifier}", edited, token).status)
    }
}

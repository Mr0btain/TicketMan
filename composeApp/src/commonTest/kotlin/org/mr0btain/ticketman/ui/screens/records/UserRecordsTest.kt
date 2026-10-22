package org.mr0btain.ticketman.ui.screens.records

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.screens.records.components.user.UserRecords
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/** Staff notes save straight away and come back from the server, and only for admins */
class UserRecordsTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val sam = User(username = "sam", name = "Sam Tech")
    private val serverNotes = mutableListOf<Note>()
    private val writes = mutableListOf<String>()

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath

        when {
            path == SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            path == "/" -> respond("", HttpStatusCode.OK)
            path.startsWith("/api/user/get/notes/") -> respond(Json.encodeToString(serverNotes.toList()), HttpStatusCode.OK, jsonHeaders)
            path.startsWith("/api/user/add/note/") || path.startsWith("/api/user/update/note/") ||
                path.startsWith("/api/user/remove/note/") -> {
                writes += "${request.method.value} $path"
                respond("", HttpStatusCode.Accepted)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
        fail("Timed out waiting")
    }

    private suspend fun TestScope.records(admin: Boolean): UserRecords {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
        remote.setUrl("http://localhost")

        return UserRecords(remote, backgroundScope, SnackbarHostState(), { admin }, OffsetPagerConfig(), 10, { 10 })
            .also { it.seed.setUser(sam, emptyList()) }
    }

    @Test
    fun aNewNoteIsAddedAndShownAsTheServerSignedIt() = runTest {
        val users = records(admin = true)
        val note = Note(message = "Trained on board repair")
        serverNotes += note.copy(author = "boss")

        users.saveNote(note)
        settle { users.seed.notes.isNotEmpty() }

        assertEquals(listOf("POST /api/user/add/note/${sam.identifier}"), writes)
        assertEquals("boss", users.seed.notes.single().author)
        assertFalse(users.seed.isDirty)
    }

    @Test
    fun anExistingNoteIsUpdated() = runTest {
        val note = Note(message = "Works Mondays")
        val users = records(admin = true).also { it.seed.setUser(sam, listOf(note)) }
        serverNotes += note.copy(message = "Works Mondays and Fridays")

        users.saveNote(note.copy(message = "Works Mondays and Fridays"))
        settle { users.seed.notes.single().message.endsWith("Fridays") }

        assertEquals(listOf("POST /api/user/update/note/${note.identifier}"), writes)
    }

    @Test
    fun aStaffNoteIsDeletedStraightAway() = runTest {
        val note = Note(message = "Works Mondays")
        val users = records(admin = true).also { it.seed.setUser(sam, listOf(note)) }

        users.removeNote(note.identifier)
        settle { users.seed.notes.isEmpty() }

        assertEquals(listOf("DELETE /api/user/remove/note/${note.identifier}"), writes)
        assertFalse(users.seed.isDirty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onlyAdminsCanDeleteStaffNotes() = runTest {
        val note = Note(message = "Works Mondays")
        val users = records(admin = false).also { it.seed.setUser(sam, listOf(note)) }

        users.removeNote(note.identifier)
        runCurrent()

        assertTrue(writes.isEmpty())
        assertEquals(listOf(note), users.seed.notes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onlyAdminsCanSaveStaffNotes() = runTest {
        val users = records(admin = false)

        users.saveNote(Note(message = "Promote me"))
        runCurrent()

        assertTrue(writes.isEmpty())
    }
}

package org.mr0btain.ticketman.ui.screens.records

import androidx.compose.material3.SnackbarHostState
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.database.TicketRemoteRepository
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.screens.records.components.syncNotes
import kotlin.test.*
import kotlin.uuid.Uuid

/** [syncNotes] sends only what this device changed since the record was opened */
class RecordNotesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val ticket = Uuid.random()

    private val kept = Note(author = "Sam", message = "Screen ordered")
    private val edited = Note(author = "Sam", message = "Waiting on parts")
    private val dropped = Note(author = "Sam", message = "Called customer")

    /** Calls the repository made, other than reading the notes, as "METHOD path" */
    private val calls = mutableListOf<String>()
    private val bodies = mutableListOf<String>()
    private var failWrites = false
    private var serverNotes = listOf(kept, edited, dropped)

    /** The notes as the record was opened with */
    private val loaded = listOf(kept, edited, dropped)

    private val repo = TicketRemoteRepository(
        HttpClient(MockEngine { request ->
            val path = request.url.encodedPath.removePrefix("/api/data/ticket")

            if (path.startsWith("/get/notes/")) {
                respond(
                    json.encodeToString(serverNotes),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                calls += "${request.method.value} $path"
                bodies += (request.body as? TextContent)?.text ?: ""
                respond("", if (failWrites) HttpStatusCode.InternalServerError else HttpStatusCode.Accepted)
            }
        }) { install(ContentNegotiation) { json(json) } }
    ).apply { serverUrl = "http://localhost" }

    @Test
    fun onlyChangesAreSent() = runTest {
        val added = Note(message = "Customer called back")
        val withPhoto = edited.copy(images = listOf(StoredImage(width = 40, height = 30)))
        val snackBar = SnackbarHostState()

        syncNotes(ticket, listOf(kept, withPhoto, added), loaded, repo, snackBar)

        assertEquals(
            setOf(
                "POST /update/note/${edited.identifier}",
                "POST /add/note/$ticket",
                "DELETE /remove/note/${dropped.identifier}"
            ),
            calls.toSet()
        )
        assertEquals(3, calls.size)
        assertNull(snackBar.currentSnackbarData)
    }

    @Test
    fun aPhotoRemovedInTheNoteEditorIsSavedWithTheRecord() = runTest {
        val photo = StoredImage(width = 40, height = 30)
        serverNotes = listOf(kept, edited.copy(images = listOf(photo)), dropped)
        val seed = TicketSeed().also { it.setTicket(Ticket(identifier = ticket), serverNotes) }

        // What the note dialog does: edit a copy, then hand it back to the record
        val noteSeed = NoteSeed(seed.notes[1])
        noteSeed.deleteImage(photo.identifier)
        seed.saveNote(noteSeed.note)
        assertTrue(seed.isDirty)

        syncNotes(ticket, seed.notes, seed.savedNotes, repo, SnackbarHostState())

        assertEquals(listOf("POST /update/note/${edited.identifier}"), calls)
        assertTrue(json.decodeFromString<Note>(bodies.single()).images.isEmpty())
    }

    @Test
    fun aNoteDeletedInTheDialogIsRemovedWithTheRecord() = runTest {
        val seed = TicketSeed().also { it.setTicket(Ticket(identifier = ticket), listOf(kept, edited, dropped)) }

        seed.deleteNote(dropped.identifier)
        syncNotes(ticket, seed.notes, seed.savedNotes, repo, SnackbarHostState())

        assertEquals(listOf("DELETE /remove/note/${dropped.identifier}"), calls)
    }

    @Test
    fun nothingIsSentWhenNothingChanged() = runTest {
        syncNotes(ticket, listOf(kept, edited, dropped), loaded, repo, SnackbarHostState())

        assertTrue(calls.isEmpty())
    }

    @Test
    fun aNoteAddedOnAnotherDeviceIsKept() = runTest {
        serverNotes = loaded + Note(author = "Priya", message = "Added at the counter")

        syncNotes(ticket, listOf(kept, edited.copy(message = "Parts here")), loaded, repo, SnackbarHostState())

        assertEquals(
            setOf("POST /update/note/${edited.identifier}", "DELETE /remove/note/${dropped.identifier}"),
            calls.toSet()
        )
        assertEquals(2, calls.size)
    }

    @Test
    fun aNoteChangedOnAnotherDeviceIsntOverwritten() = runTest {
        // Edited there, then edited here; and deleted here after being edited there
        serverNotes = listOf(kept, edited.copy(message = "Edited there"), dropped.copy(message = "Also edited there"))
        val snackBar = SnackbarHostState()

        val sync = launch { syncNotes(ticket, listOf(kept, edited.copy(message = "Edited here")), loaded, repo, snackBar) }

        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (snackBar.currentSnackbarData == null) delay(10) }
        }

        assertTrue(calls.isEmpty())
        assertEquals(
            "2 notes were changed on another device since this was opened, so your changes to them weren't saved",
            snackBar.currentSnackbarData?.visuals?.message
        )
        snackBar.currentSnackbarData?.dismiss()
        sync.join()
    }

    @Test
    fun aNoteAlreadyGoneIsntRemovedAgain() = runTest {
        serverNotes = listOf(kept, edited)

        syncNotes(ticket, listOf(kept, edited), loaded, repo, SnackbarHostState())

        assertTrue(calls.isEmpty())
    }

    @Test
    fun failuresAreCountedInOneSnackbar() = runTest {
        failWrites = true
        val snackBar = SnackbarHostState()

        // Showing a snackbar waits for it to go away, so sync in the background and look while it's up
        val sync = launch { syncNotes(ticket, listOf(kept, Note(message = "New")), loaded, repo, snackBar) }

        // The mock engine answers on its own threads, so wait on a real clock
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (snackBar.currentSnackbarData == null) delay(10) }
        }

        assertEquals("3 note changes weren't saved", snackBar.currentSnackbarData?.visuals?.message)
        snackBar.currentSnackbarData?.dismiss()
        sync.join()
    }
}

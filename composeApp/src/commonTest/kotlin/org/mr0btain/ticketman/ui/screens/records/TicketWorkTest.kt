package org.mr0btain.ticketman.ui.screens.records

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketWork
import kotlin.test.*

/**
 * The open ticket's work log on the client: loading it, logging and taking work off
 */
class TicketWorkTest {
    private val ticket = Ticket()
    private val other = Ticket()
    private val screen = WorkEntry(type = WorkType.LABOR, description = "Replaced the screen", quantity = 1.5, unitPriceCents = 8_000)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val logged = mutableListOf<WorkRequest>()
    private var removeStatus = HttpStatusCode.Accepted
    private var workFails = false

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath

        when {
            path == SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            path == "/api/data/ticket/get/work/${ticket.identifier}" ->
                if (workFails) respond("", HttpStatusCode.InternalServerError)
                else respond(Json.encodeToString(listOf(screen)), HttpStatusCode.OK, jsonHeaders)
            path == "/api/data/ticket/get/work/${other.identifier}" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            path == "/api/data/ticket/add/work/${ticket.identifier}" -> {
                val body = Json.decodeFromString<WorkRequest>((request.body as TextContent).text)
                logged += body
                val saved = WorkEntry(type = body.type, description = body.description, quantity = body.quantity, unitPriceCents = body.unitPriceCents ?: 0, userName = "tech")
                respond(Json.encodeToString(saved), HttpStatusCode.Created, jsonHeaders)
            }
            path.startsWith("/api/data/ticket/remove/work/") ->
                respond(if (removeStatus == HttpStatusCode.Accepted) "" else "Invoice INV-26-00000001 is paid, its work can't change", removeStatus)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private val snackBar = SnackbarHostState()

    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        fail("Timed out waiting")
    }

    private suspend fun TestScope.work(canEdit: Boolean = true): TicketWork {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
        return TicketWork(remote, backgroundScope, snackBar) { canEdit }
    }

    @Test
    fun loadingThenLoggingAddsToTheList() = runTest {
        val work = work()
        work.load(ticket.identifier)
        settle { work.entries.value != null }
        assertEquals(listOf(screen), work.entries.value)

        var result: String? = "not yet"
        work.log(WorkRequest(WorkType.LABOR, "Cleaned the port", quantity = 0.5, unitPriceCents = 8_000)) { result = it }
        settle { result != "not yet" }

        assertNull(result)
        assertEquals(listOf("Replaced the screen", "Cleaned the port"), work.entries.value?.map { it.description })
        assertEquals(0.5, logged.single().quantity)
    }

    @Test
    fun aRefusedRemoveKeepsTheEntryAndSaysWhy() = runTest {
        val work = work()
        work.load(ticket.identifier)
        settle { work.entries.value != null }
        removeStatus = HttpStatusCode.Conflict

        work.remove(screen)
        settle { snackBar.currentSnackbarData != null }

        assertEquals("Work not removed: Invoice INV-26-00000001 is paid, its work can't change", snackBar.currentSnackbarData?.visuals?.message)
        assertEquals(listOf(screen), work.entries.value)
    }

    @Test
    fun aRemoveTakesItOff() = runTest {
        val work = work()
        work.load(ticket.identifier)
        settle { work.entries.value != null }

        work.remove(screen)
        settle { work.entries.value?.isEmpty() == true }
    }

    @Test
    fun aFailedLoadSaysSoUnlessQuiet() = runTest {
        val work = work()
        workFails = true

        work.load(ticket.identifier, quiet = true)
        repeat(20) { runCurrent(); withContext(Dispatchers.Default) { delay(5) } }
        assertNull(work.loadError.value)

        work.load(ticket.identifier)
        settle { work.loadError.value != null }
        assertNull(work.entries.value)
    }

    @Test
    fun anotherTicketStartsEmpty() = runTest {
        val work = work()
        work.load(ticket.identifier)
        settle { work.entries.value != null }

        work.load(other.identifier)
        settle { work.entries.value?.isEmpty() == true }
    }

    @Test
    fun restrictedUsersCantLog() = runTest {
        val work = work(canEdit = false)
        work.load(ticket.identifier)
        settle { work.entries.value != null }

        var result: String? = null
        work.log(WorkRequest(WorkType.LABOR, "Time", quantity = 1.0)) { result = it }

        assertEquals("You can only look, not change things", result)
        assertTrue(logged.isEmpty())
    }
}

package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import org.mr0btain.ticketman.ui.screens.counter.components.CounterHandler
import org.mr0btain.ticketman.ui.screens.counter.components.CounterView
import kotlin.test.*

class CounterHandlerTest {
    private val ticket = Ticket(number = TicketNumber(2026, 42))
    private val requests = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"

        when (request.url.encodedPath) {
            "/" -> respond("", HttpStatusCode.OK)
            "/api/counter/search" -> respond(Json.encodeToString(CounterSearchResponse(tickets = listOf(TicketSummary(ticket)))), HttpStatusCode.OK, jsonHeaders)
            "/api/data/ticket/get" -> respond(Json.encodeToString(ticket), HttpStatusCode.OK, jsonHeaders)
            "/api/data/ticket/get/notes/${ticket.identifier}", "/api/data/ticket/get/history/${ticket.identifier}" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    /**
     * Requests finish on the mock engine's own threads, so keep running the test's coroutines until [done] or it
     * clearly isn't going to happen
     */
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            advanceUntilIdle()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private suspend fun TestScope.handler(canEdit: Boolean): CounterHandler {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
        remote.setUrl("http://localhost")
        requests.clear()

        return CounterHandler(remote, this, SnackbarHostState(), canEdit = { canEdit })
    }

    @Test
    fun searchWaitsForThemToStopTyping() = runTest {
        val counter = handler(canEdit = true)

        counter.setQuery("2")
        counter.setQuery("26")
        counter.setQuery("26-42")
        settle { counter.results.value.tickets.isNotEmpty() }

        assertEquals(1, requests.count { it == "POST /api/counter/search" })
        assertEquals(ticket.identifier, counter.results.value.tickets.single().ticket.identifier)
    }

    @Test
    fun clearingTheSearchSkipsTheServer() = runTest {
        val counter = handler(canEdit = true)

        counter.setQuery("")
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
    }

    @Test
    fun openingATicketLoadsItsDetails() = runTest {
        val counter = handler(canEdit = true)

        counter.openTicket(ticket.identifier)
        settle { counter.view.value == CounterView.TICKET }

        assertEquals(CounterView.TICKET, counter.view.value)
        assertEquals("26-00000042", counter.ticket.value?.ticket?.label)
        assertNull(counter.ticket.value?.customer)
    }

    @Test
    fun viewOnlyUsersCantChangeStatus() = runTest {
        val counter = handler(canEdit = false)
        counter.openTicket(ticket.identifier)
        settle { counter.ticket.value != null }
        requests.clear()

        counter.setTicketStatus(TicketStatusType.DONE)
        counter.addTicketNote("Called the customer")

        // Both give up before launching anything when the user can't edit
        advanceUntilIdle()

        assertTrue(requests.isEmpty(), "Restricted user sent $requests")
    }

    @Test
    fun doneReadsAsReadyForPickup() {
        assertEquals("Ready for pickup", CounterHandler.statusLabel(TicketStatusType.DONE))
    }
}

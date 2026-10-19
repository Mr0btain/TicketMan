package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
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
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceNumber
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import org.mr0btain.ticketman.ui.screens.counter.components.CounterHandler
import org.mr0btain.ticketman.ui.screens.counter.components.CounterView
import kotlin.test.*
import kotlin.uuid.Uuid

class CounterHandlerTest {
    private val ticket = Ticket(number = TicketNumber(2026, 42))
    private val invoice = Invoice(ticketIdentifier = ticket.identifier, number = InvoiceNumber(2026, 1))
    private val requests = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"

        when (request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/" -> respond("", HttpStatusCode.OK)
            "/api/counter/search" -> respond(Json.encodeToString(CounterSearchResponse(tickets = listOf(TicketSummary(ticket)))), HttpStatusCode.OK, jsonHeaders)
            "/api/data/ticket/get/${ticket.identifier}" -> respond(Json.encodeToString(ticket), HttpStatusCode.OK, jsonHeaders)
            "/api/counter/check-in" -> respond(Json.encodeToString(CheckInResponse(ticket)), HttpStatusCode.Created, jsonHeaders)
            "/api/invoice/ticket/${ticket.identifier}" -> respond(Json.encodeToString(invoice), HttpStatusCode.OK, jsonHeaders)
            "/api/invoice/${invoice.identifier}/update" -> respond(Json.encodeToString(invoice.copy(totalCents = 4_000)), HttpStatusCode.OK, jsonHeaders)
            "/api/counter/pickup" -> respond(Json.encodeToString(invoice.copy(status = InvoiceStatus.PAID)), HttpStatusCode.OK, jsonHeaders)
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

        assertEquals(1, requests.count { it == "GET /api/counter/search" })
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

    @Test
    fun finishedCheckInShowsTheNewNumber() = runTest {
        val counter = handler(canEdit = true)
        var error: String? = "not called"

        counter.checkIn.start()
        assertEquals(CounterView.CHECK_IN, counter.view.value)

        counter.checkIn.submit(CheckInRequest(Ticket(), Uuid.random())) { error = it }
        settle { counter.view.value == CounterView.CHECKED_IN }

        assertNull(error)
        assertEquals("26-00000042", counter.checkIn.last.value?.ticket?.label)
    }

    @Test
    fun viewOnlyUsersCantCheckIn() = runTest {
        val counter = handler(canEdit = false)

        counter.checkIn.start()
        counter.checkIn.submit(CheckInRequest(Ticket(), Uuid.random())) {}
        advanceUntilIdle()

        assertEquals(CounterView.HOME, counter.view.value)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun pickupSavesChangedLinesBeforeFinishing() = runTest {
        val counter = handler(canEdit = true)

        counter.pickup.start(ticket.identifier)
        settle { counter.view.value == CounterView.PICKUP }
        assertEquals(invoice.identifier, counter.pickup.detail.value?.invoice?.identifier)
        requests.clear()

        var error: String? = "not called"
        counter.pickup.finish(
            charges = listOf(Charge(description = "Labor", unitPriceCents = 4_000)),
            discountCents = 0,
            payments = listOf(PaymentRequest(PaymentMethod.CASH, 4_000))
        ) { error = it }
        settle { counter.view.value == CounterView.PICKED_UP }

        assertNull(error)
        assertEquals(listOf("POST /api/invoice/${invoice.identifier}/update", "POST /api/counter/pickup"), requests.filter { "queue" !in it && "shop" !in it })
        assertEquals(InvoiceStatus.PAID, counter.pickup.last.value?.status)
    }

    @Test
    fun viewOnlyUsersCantStartAPickup() = runTest {
        val counter = handler(canEdit = false)

        counter.pickup.start(ticket.identifier)
        advanceUntilIdle()

        assertEquals(CounterView.HOME, counter.view.value)
        assertTrue(requests.isEmpty())
    }
}

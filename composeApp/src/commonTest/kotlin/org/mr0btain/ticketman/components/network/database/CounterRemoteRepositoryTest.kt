package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.RepeatCheckRequest
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Counter, ticket and customer client calls hit the routes the server has
 */
class CounterRemoteRepositoryTest {
    private val requests = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val created = Ticket(number = TicketNumber(2026, 7))

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"

        when (request.url.encodedPath) {
            "/api/counter/queue" -> respond(Json.encodeToString(CounterQueueResponse()), HttpStatusCode.OK, jsonHeaders)
            "/api/counter/search" -> respond("""{"tickets":[],"customers":[]}""", HttpStatusCode.OK, jsonHeaders)
            "/api/counter/repeat-check" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            "/api/data/ticket/add" -> respond(Json.encodeToString(created), HttpStatusCode.Created, jsonHeaders)
            "/api/counter/pickup" -> respond("$12.00 is still owed on INV-26-00000001", HttpStatusCode.Conflict)
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val counter = CounterRemoteRepository(client).apply { serverUrl = "http://localhost" }
    private val tickets = TicketRemoteRepository(client).apply { serverUrl = "http://localhost" }
    private val customers = CustomerRemoteRepository(client).apply { serverUrl = "http://localhost" }

    @Test
    fun counterRoutes() = runTest {
        assertNotNull(counter.queue(5))
        counter.search("555", 10)
        counter.repeatCheck(RepeatCheckRequest(Uuid.random(), Device()))

        assertEquals(listOf("GET /api/counter/queue", "GET /api/counter/search", "POST /api/counter/repeat-check"), requests)
    }

    @Test
    fun blankSearchSkipsTheRequest() = runTest {
        counter.search("  ", 10)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun addReturnsTheNumberedTicket() = runTest {
        val result = tickets.add(Ticket())

        assertIs<Result.Success<Ticket>>(result)
        assertEquals("26-00000007", result.data.number.toString())
    }

    @Test
    fun ticketFetchRoutes() = runTest {
        tickets.fetch(0, 20)
        tickets.fetchMine(20, 20)

        assertEquals(listOf("GET /api/data/ticket/fetch", "GET /api/data/ticket/fetch/mine"), requests)
    }

    @Test
    fun ticketAndCustomerLinkRoutes() = runTest {
        val id = Uuid.parse("00000000-0000-7000-8000-000000000001")

        tickets.getStatusHistory(id)
        tickets.getByNumber(TicketNumber(2026, 7))
        tickets.setCustomer(id, id)
        tickets.setUser(id, id)
        tickets.getNotes(id)
        customers.addTicket(id, id)
        customers.getTickets(id)

        assertEquals(
            listOf(
                "GET /api/data/ticket/get/history/$id",
                "GET /api/data/ticket/number/26-00000007",
                "POST /api/data/ticket/set/customer/$id",
                "POST /api/data/ticket/set/user/$id",
                "GET /api/data/ticket/get/notes/$id",
                "POST /api/data/customer/add/ticket/$id",
                "GET /api/data/customer/get/tickets/$id"
            ),
            requests
        )
    }

    @Test
    fun pickupPassesOnWhatsStillOwed() = runTest {
        val result = counter.pickup(Uuid.random(), listOf(PaymentRequest(PaymentMethod.CASH, 100)))

        assertIs<Result.Error<*>>(result)
        assertEquals("$12.00 is still owed on INV-26-00000001", (result as Result.Error).error.message)
    }

    @Test
    fun invoiceRoutes() = runTest {
        val invoices = InvoiceRemoteRepository(client).apply { serverUrl = "http://localhost" }
        val id = Uuid.parse("00000000-0000-7000-8000-000000000001")

        invoices.getForTicket(id)
        invoices.openForTicket(id)
        invoices.update(id, emptyList(), 0)
        invoices.addPayment(id, PaymentRequest(PaymentMethod.CARD, 500))
        invoices.removePayment(id, id)
        invoices.void(id)
        invoices.fetch(0, 10)

        assertEquals(
            listOf(
                "GET /api/invoice/ticket/$id",
                "POST /api/invoice/ticket/$id",
                "POST /api/invoice/$id/update",
                "POST /api/invoice/$id/payments",
                "DELETE /api/invoice/$id/payments/$id",
                "POST /api/invoice/$id/void",
                "GET /api/invoice/fetch"
            ),
            requests
        )
    }
}

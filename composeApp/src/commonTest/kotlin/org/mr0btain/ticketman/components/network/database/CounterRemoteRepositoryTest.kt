package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.error.Result
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
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val counter = CounterRemoteRepository(client).apply { serverUrl = "http://localhost" }
    private val tickets = TicketRemoteRepositoryImpl(client).apply { serverUrl = "http://localhost" }
    private val customers = CustomerRemoteRepositoryImpl(client).apply { serverUrl = "http://localhost" }

    @Test
    fun counterRoutes() = runTest {
        assertNotNull(counter.queue(5))
        counter.search("555", 10)
        counter.repeatCheck(RepeatCheckRequest(Uuid.random(), Device()))

        assertEquals(listOf("GET /api/counter/queue", "POST /api/counter/search", "POST /api/counter/repeat-check"), requests)
    }

    @Test
    fun blankSearchSkipsTheRequest() = runTest {
        counter.search("  ", 10)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun createReturnsTheNumberedTicket() = runTest {
        val result = tickets.create(Ticket())

        assertIs<Result.Success<Ticket>>(result)
        assertEquals("26-00000007", result.data.number.toString())
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
                "POST /api/data/ticket/get/notes/$id",
                "POST /api/data/customer/add/ticket/$id",
                "GET /api/data/customer/get/tickets/$id"
            ),
            requests
        )
    }
}

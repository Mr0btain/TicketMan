package org.mr0btain.ticketman.ui.screens.records

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
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
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerRecords
import kotlin.test.*

/**
 * Linking an existing ticket from the customer dialog: finding out whose it is now, then saving the link
 */
class CustomerTicketLinkTest {
    private val jane = Customer(name = "Jane Doe", phone = "5550142")
    private val bob = EntityReference(Customer(name = "Bob").identifier, "Bob")
    private val loose = Ticket(device = Device("Apple", "iPhone 14"))
    private val bobs = Ticket(device = Device("Google", "Pixel 8"))
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val linked = mutableListOf<String>()

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath

        when {
            path == SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            path == "/api/data/customer/get/${jane.identifier}" -> respond(Json.encodeToString(jane), HttpStatusCode.OK, jsonHeaders)
            path == "/api/data/ticket/get/${loose.identifier}" -> respond(Json.encodeToString(loose), HttpStatusCode.OK, jsonHeaders)
            path == "/api/data/ticket/get/${bobs.identifier}" -> respond(Json.encodeToString(bobs), HttpStatusCode.OK, jsonHeaders)
            path == "/api/data/ticket/get/customer/${bobs.identifier}" -> respond(Json.encodeToString(bob), HttpStatusCode.OK, jsonHeaders)
            path.startsWith("/api/data/customer/add/ticket/") -> {
                linked += path.substringAfterLast('/') + " " + (request.body as io.ktor.http.content.TextContent).text
                respond("", HttpStatusCode.Accepted)
            }
            path.contains("/get/notes/") || path.contains("/get/tickets/") || path.endsWith("/fetch") ->
                respond("[]", HttpStatusCode.OK, jsonHeaders)
            // No customer on the loose ticket, and anything else
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        fail("Timed out waiting")
    }

    private suspend fun TestScope.records(): CustomerRecords {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
        return CustomerRecords(remote, backgroundScope, SnackbarHostState(), { true }, OffsetPagerConfig(), 10, { 10 })
    }

    @Test
    fun aTicketWithNoCustomerHasNoneToMoveFrom() = runTest {
        val found = records().ticketToLink(loose.identifier)

        assertIs<TmResult.Success<*>>(found)
        val link = (found as TmResult.Success).data
        assertEquals(loose.identifier, link.ticket.identifier)
        assertNull(link.currentCustomer)
    }

    @Test
    fun aTicketOnAnotherCustomerSaysWhose() = runTest {
        val found = records().ticketToLink(bobs.identifier)

        assertEquals(bob, (found as TmResult.Success).data.currentCustomer)
    }

    @Test
    fun aMissingTicketIsAnError() = runTest {
        assertIs<TmResult.Error<*>>(records().ticketToLink(Ticket().identifier))
    }

    @Test
    fun savingSendsTheNewLink() = runTest {
        val records = records()
        records.select(jane.identifier)
        settle { records.seed.identifier == jane.identifier && !records.seed.isNew }

        records.seed.addTicket(loose)
        assertTrue(records.seed.isDirty)
        records.save()
        settle { linked.isNotEmpty() }

        assertEquals(listOf("${jane.identifier} \"${loose.identifier}\""), linked)
    }
}

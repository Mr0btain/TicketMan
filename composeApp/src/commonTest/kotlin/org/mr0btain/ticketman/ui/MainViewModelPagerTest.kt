package org.mr0btain.ticketman.ui

import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import kotlin.test.*

/**
 * The records screen collects the pagers' items as soon as it shows, which can be before startup has reached the server.
 * Whatever it collected then has to be what gets filled
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelPagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val customers = listOf(Customer(name = "Ada"), Customer(name = "Grace"))
    private val tickets = listOf(Ticket())
    private val inventory = listOf(Inventory())

    private val engine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/" -> respond("", HttpStatusCode.OK)
            "/api/data/customer/fetch" -> respond(Json.encodeToString(customers), HttpStatusCode.OK, jsonHeaders)
            "/api/data/ticket/fetch", "/api/data/ticket/fetch/mine" -> respond(Json.encodeToString(tickets), HttpStatusCode.OK, jsonHeaders)
            "/api/data/inventory/fetch" -> respond(Json.encodeToString(inventory), HttpStatusCode.OK, jsonHeaders)
            "/api/data/user/fetch" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private val localStorage = LocalStorage(InMemoryKeyValueStore()).apply { serverUrl = "http://localhost" }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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

    @Test
    fun itemsCollectedBeforeStartupGetTheFirstLoad() = runTest(dispatcher) {
        val viewModel = MainViewModel(SnackbarHostState(), localStorage, engine)

        // What the records screen grabs on its first composition
        val customerItems = viewModel.customers.pager.items
        val ticketItems = viewModel.tickets.pager.items
        val inventoryItems = viewModel.inventory.pager.items

        viewModel.init()
        settle { customerItems.value.isNotEmpty() && ticketItems.value.isNotEmpty() && inventoryItems.value.isNotEmpty() }

        assertEquals(customers.map { it.identifier }, customerItems.value.map { it.identifier })
        assertEquals(tickets.map { it.identifier }, ticketItems.value.map { it.identifier })
        assertEquals(inventory.map { it.identifier }, inventoryItems.value.map { it.identifier })
    }

    @Test
    fun pageSizeChangeKeepsTheSamePagers() = runTest(dispatcher) {
        val viewModel = MainViewModel(SnackbarHostState(), localStorage, engine)
        val pager = viewModel.customers.pager
        viewModel.init()
        settle { pager.items.value.isNotEmpty() }

        viewModel.settings.setPageSize(viewModel.settings.pageSize.value + 5)
        settle { pager.items.value.isNotEmpty() && !pager.isRefreshing }

        assertSame(pager, viewModel.customers.pager)
        assertEquals(customers.size, pager.items.value.size)
    }

    @Test
    fun loggingOutDropsLoadedRecords() = runTest(dispatcher) {
        val viewModel = MainViewModel(SnackbarHostState(), localStorage, engine)
        val pager = viewModel.customers.pager
        viewModel.init()
        settle { pager.items.value.isNotEmpty() }

        viewModel.session.logout()
        settle { pager.items.value.isEmpty() }

        assertTrue(pager.items.value.isEmpty())
        assertNull(viewModel.session.currentUser.value)
    }
}

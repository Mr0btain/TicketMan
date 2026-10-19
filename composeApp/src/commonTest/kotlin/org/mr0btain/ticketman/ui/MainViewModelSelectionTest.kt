package org.mr0btain.ticketman.ui

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.*

/**
 * Clicking customer A then customer B has to end on B, even when A's details come back last
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val slow = Customer(name = "Slow")
    private val fast = Customer(name = "Fast")

    // Holds back the slow customer's reply until the test lets it go
    private val release = CompletableDeferred<Unit>()

    private val engine = MockEngine { request ->
        when (val path = request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/" -> respond("", HttpStatusCode.OK)
            "/api/data/customer/get/${slow.identifier}", "/api/data/customer/get/${fast.identifier}" -> {
                val customer = if (path.endsWith(slow.identifier.toString())) slow.also { release.await() } else fast
                respond(Json.encodeToString(customer), HttpStatusCode.OK, jsonHeaders)
            }
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
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
    fun theLastClickWinsEvenWhenAnEarlierOneFinishesLater() = runTest(dispatcher) {
        val viewModel = MainViewModel(SnackbarHostState(), localStorage, engine)
        viewModel.init()
        settle { viewModel.session.serverConnected.value }

        viewModel.customers.select(slow.identifier)
        assertEquals(slow.identifier, viewModel.customers.selection.pending.value)

        viewModel.customers.select(fast.identifier)
        assertEquals(fast.identifier, viewModel.customers.selection.pending.value)
        settle { viewModel.customers.seed.identifier == fast.identifier }

        // The slow reply lands after the fast one is already showing
        release.complete(Unit)
        settle { false }

        assertEquals(fast.identifier, viewModel.customers.seed.identifier)
        assertNull(viewModel.customers.selection.pending.value)
    }
}

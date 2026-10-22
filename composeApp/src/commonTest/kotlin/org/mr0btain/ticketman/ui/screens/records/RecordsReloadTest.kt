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
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerRecords
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * The periodic check for updates reloading the open record: it picks up another device's change, leaves an edit
 * alone, and stays quiet when the server can't be reached
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsReloadTest {
    private var jane = Customer(name = "Jane Doe", phone = "5550142")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private var serverDown = false
    private var customerGets = 0

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath

        when {
            path == SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            serverDown -> respond("", HttpStatusCode.InternalServerError)
            path == "/api/data/customer/get/${jane.identifier}" -> {
                customerGets++
                respond(Json.encodeToString(jane), HttpStatusCode.OK, jsonHeaders)
            }
            path.contains("/get/notes/") || path.contains("/get/tickets/") || path.endsWith("/fetch") ->
                respond("[]", HttpStatusCode.OK, jsonHeaders)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private val snackBar = SnackbarHostState()

    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
        fail("Timed out waiting")
    }

    // Lets the background scope run a little, for checks that something didn't happen
    private suspend fun TestScope.idle() {
        repeat(20) {
            runCurrent()
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
    }

    private suspend fun TestScope.withJaneOpen(): CustomerRecords {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
        val records = CustomerRecords(remote, backgroundScope, snackBar, { true }, OffsetPagerConfig(), 10, { 10 })

        records.select(jane.identifier)
        settle { records.seed.identifier == jane.identifier && !records.seed.isNew }
        return records
    }

    @Test
    fun anotherDevicesChangeShowsUp() = runTest {
        val records = withJaneOpen()
        jane = jane.copy(phone = "5550199")

        records.reloadSelected()
        settle { records.seed.phone == "5550199" }

        assertFalse(records.seed.isDirty)
    }

    @Test
    fun anEditIsLeftAlone() = runTest {
        val records = withJaneOpen()
        val gets = customerGets
        records.seed.changePhone("5550111")

        records.reloadSelected()
        idle()

        assertEquals(gets, customerGets)
        assertEquals("5550111", records.seed.phone)
    }

    @Test
    fun aServerThatCantBeReachedSaysNothing() = runTest {
        val records = withJaneOpen()
        serverDown = true

        records.reloadSelected()
        idle()

        assertNull(snackBar.currentSnackbarData)
        assertEquals("Jane Doe", records.seed.name)
    }
}

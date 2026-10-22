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
 * Deleting a record waits for the server, and a record that's gone from the server stops being shown
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsRemoveTest {
    private val jane = Customer(name = "Jane Doe", phone = "5550142")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    // What the server does with a delete, and whether Jane is still there
    private var removeStatus = HttpStatusCode.Accepted
    private var removeBody = ""
    private var janeExists = true
    private var notesFail = false

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath

        when {
            path == SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            path == "/" -> respond("", HttpStatusCode.OK)
            path == "/api/data/customer/get/${jane.identifier}" ->
                if (janeExists) respond(Json.encodeToString(jane), HttpStatusCode.OK, jsonHeaders)
                else respond("", HttpStatusCode.NotFound)
            path.contains("/get/notes/") && notesFail -> respond("", HttpStatusCode.InternalServerError)
            path.contains("/get/notes/") || path.contains("/get/tickets/") || path.endsWith("/fetch") ->
                respond("[]", HttpStatusCode.OK, jsonHeaders)
            path.contains("/remove/") -> respond(removeBody, removeStatus)
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

    // Jane, opened in the seed
    private suspend fun TestScope.withJaneOpen(): CustomerRecords {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
        val records = CustomerRecords(remote, backgroundScope, snackBar, { true }, OffsetPagerConfig(), 10, { 10 })

        records.select(jane.identifier)
        settle { records.seed.identifier == jane.identifier && !records.seed.isNew }
        return records
    }

    @Test
    fun aRefusedDeleteKeepsTheRecordAndSaysWhy() = runTest {
        val records = withJaneOpen()
        removeStatus = HttpStatusCode.Conflict
        removeBody = "Customer has open tickets"

        records.remove(jane.identifier)
        settle { snackBar.currentSnackbarData != null }

        assertEquals("Customer not deleted: Customer has open tickets", snackBar.currentSnackbarData?.visuals?.message)
        assertEquals(jane.identifier, records.seed.identifier)
        assertFalse(records.seed.isNew)
    }

    @Test
    fun aDeleteTheServerTakesClearsTheRecord() = runTest {
        val records = withJaneOpen()

        records.remove(jane.identifier)
        settle { records.seed.isNew }

        assertNotEquals(jane.identifier, records.seed.identifier)
        assertNull(snackBar.currentSnackbarData)
    }

    @Test
    fun reloadingARecordDeletedElsewhereStopsShowingIt() = runTest {
        val records = withJaneOpen()
        records.seed.changePhone("5550199")

        // Another device deleted her, then Cancel reloads the record
        janeExists = false
        records.cache.remove(jane.identifier)
        records.select(jane.identifier)
        settle { records.seed.isNew && snackBar.currentSnackbarData != null }

        assertNotEquals(jane.identifier, records.seed.identifier)
        assertTrue(snackBar.currentSnackbarData?.visuals?.message.orEmpty().contains("may have been deleted"))
    }

    @Test
    fun aRecordThatCouldntBeLoadedStaysAndSaysWhy() = runTest {
        val records = withJaneOpen()

        // Her notes can't be read, which mustn't look like she has none
        notesFail = true
        records.cache.remove(jane.identifier)
        records.select(jane.identifier)
        settle { snackBar.currentSnackbarData != null }

        assertEquals(jane.identifier, records.seed.identifier)
        assertFalse(records.seed.isNew)
        assertTrue(snackBar.currentSnackbarData?.visuals?.message.orEmpty().startsWith("That customer couldn't be loaded"))
    }
}

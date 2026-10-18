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
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerRecords
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryRecords
import kotlin.test.*

/**
 * Restricted users can look at records but the handlers don't send their changes, like the counter
 */
class RecordsAccessTest {
    private val requests = mutableListOf<String>()

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"

        when (request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/" -> respond("", HttpStatusCode.OK)
            else -> respond("", HttpStatusCode.Accepted)
        }
    }

    // Writes only, the pagers may load pages in the background
    private val writes get() = requests.filter { it.contains("/add") || it.contains("/update") || it.contains("/remove") }

    private suspend fun TestScope.settle() {
        repeat(20) {
            runCurrent()
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private suspend fun TestScope.remote(): RemoteHandler =
        RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also {
            it.setUrl("http://localhost")
            requests.clear()
        }

    private suspend fun TestScope.customers(canEdit: Boolean) =
        CustomerRecords(remote(), backgroundScope, SnackbarHostState(), { canEdit }, OffsetPagerConfig(), 10, { 10 })

    private suspend fun TestScope.inventory(canEdit: Boolean) =
        InventoryRecords(remote(), backgroundScope, SnackbarHostState(), { canEdit }, OffsetPagerConfig(), 10, { 10 })

    @Test
    fun viewOnlyUsersCantSaveACustomer() = runTest {
        val records = customers(canEdit = false)
        records.seed.changePhone("555 0100")

        records.save()
        settle()

        assertEquals(emptyList(), writes)
    }

    @Test
    fun editorsCanSaveACustomer() = runTest {
        val records = customers(canEdit = true)
        records.seed.changePhone("555 0100")

        records.save()
        settle()

        assertTrue(writes.any { it.contains("/api/data/customer/add") }, "Sent: $requests")
    }

    @Test
    fun viewOnlyUsersCantDeleteAnItem() = runTest {
        val records = inventory(canEdit = false)

        records.remove(records.seed.identifier)
        settle()

        assertEquals(emptyList(), writes)
    }

    @Test
    fun editorsCanDeleteAnItem() = runTest {
        val records = inventory(canEdit = true)

        records.remove(records.seed.identifier)
        settle()

        assertTrue(writes.any { it.contains("/api/data/inventory/remove/") }, "Sent: $requests")
    }
}

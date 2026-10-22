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
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.server.request.StockChangeRequest
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryRecords
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stock counts are saved on their own, from the count the item was opened at, so a pickup since isn't undone
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryStockTest {
    private var item = Inventory(name = "Screen", stock = 5, priceCents = 8_000)
    private val writes = mutableListOf<Pair<String, String>>()

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        if (path.contains("/set/") || path.contains("/add") || path.contains("/update")) {
            writes += path to (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
        }

        when {
            path == SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            path == "/" -> respond("", HttpStatusCode.OK)
            path == "/api/data/inventory/get/${item.identifier}" || path.endsWith("/add") ->
                respond(Json.encodeToString(item), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("/get/") || path.endsWith("/fetch") -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            else -> respond("", HttpStatusCode.Accepted)
        }
    }

    private suspend fun TestScope.settle() {
        repeat(20) {
            runCurrent()
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
    }

    private suspend fun TestScope.opened(): InventoryRecords {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
        return InventoryRecords(remote, backgroundScope, SnackbarHostState(), { true }, OffsetPagerConfig(), 10, { 10 }).also {
            it.select(item.identifier)
            settle()
            writes.clear()
        }
    }

    @Test
    fun aNewCountIsSentFromTheOneThatWasOpened() = runTest {
        val records = opened()
        records.seed.changeStock(12)

        records.save()
        settle()

        val (path, body) = writes.single()
        assertEquals("/api/data/inventory/set/stock/${item.identifier}", path)
        assertEquals(StockChangeRequest(from = 5, to = 12), Json.decodeFromString(body))
    }

    @Test
    fun stoppingTrackingSendsTheUntrackedCount() = runTest {
        val records = opened()
        records.seed.changeTracksStock(false)
        assertFalse(records.seed.tracksStock)

        records.save()
        settle()

        assertEquals(StockChangeRequest(from = 5, to = Int.MAX_VALUE), Json.decodeFromString(writes.single().second))
    }

    @Test
    fun anUntrackedItemStartsCountingFromZero() = runTest {
        item = item.copy(stock = Int.MAX_VALUE)
        val records = opened()
        assertFalse(records.seed.tracksStock)

        records.seed.changeTracksStock(true)
        assertEquals(0, records.seed.stock)

        records.save()
        settle()

        assertEquals(StockChangeRequest(from = Int.MAX_VALUE, to = 0), Json.decodeFromString(writes.single().second))
    }

    @Test
    fun turningTrackingOffAndOnAgainIsNoChange() = runTest {
        val records = opened()
        records.seed.changeTracksStock(false)
        records.seed.changeTracksStock(true)

        assertEquals(5, records.seed.stock)
        assertFalse(records.seed.isDirty)
    }

    @Test
    fun otherChangesDontSendTheCount() = runTest {
        val records = opened()
        records.seed.changePrice(75.0)

        records.save()
        settle()

        assertEquals(listOf("/api/data/inventory/update"), writes.map { it.first })
    }
}

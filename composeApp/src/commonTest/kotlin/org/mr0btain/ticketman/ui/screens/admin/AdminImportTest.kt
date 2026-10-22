package org.mr0btain.ticketman.ui.screens.admin

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.importing.ImportField
import org.mr0btain.ticketman.components.data.importing.ImportKind
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.server.response.ImportResult
import org.mr0btain.ticketman.server.response.ImportRowResult
import org.mr0btain.ticketman.ui.screens.admin.components.AdminImport
import org.mr0btain.ticketman.ui.screens.admin.components.AdminSupport
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * The import preview: rows the client couldn't read stay in place, and the server's answer for the rest lines up
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminImportTest {
    private val sent = mutableListOf<Pair<String, List<Customer>>>()
    private var imported = 0

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath

        when (path) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/api/admin/import/customers" -> {
                val customers = Json.decodeFromString<List<Customer>>((request.body as TextContent).text)
                val dryRun = request.url.parameters["dryRun"] == "true"
                sent += (if (dryRun) "dry" else "real") to customers
                // Bob is already on the server
                val rows = customers.mapIndexed { index, customer ->
                    if (customer.name == "Bob") ImportRowResult(index, ImportOutcome.MATCH, "Bob has that phone number") else ImportRowResult(index, ImportOutcome.ADD)
                }
                val added = if (dryRun) 0 else rows.count { it.outcome == ImportOutcome.ADD }
                respond(Json.encodeToString(ImportResult(rows, added, dryRun)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
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

    private suspend fun TestScope.importer(): AdminImport {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore())).also { it.setUrl("http://localhost") }
        return AdminImport(remote, AdminSupport(backgroundScope, snackBar, reloadAudit = {}), onImported = { imported++ })
    }

    @Test
    fun thePreviewKeepsTheFilesRowsInOrder() = runTest {
        val import = importer()

        assertNull(import.open(ImportKind.CUSTOMERS, "Name,Phone\nJane,5550142\n,5550111\nBob,5550199\nCara,5550123"))
        settle { import.preview.value != null }

        val rows = import.preview.value!!.rows
        assertEquals(listOf(2, 3, 4, 5), rows.map { it.line })
        assertEquals(listOf(ImportOutcome.ADD, ImportOutcome.ERROR, ImportOutcome.MATCH, ImportOutcome.ADD), rows.map { it.outcome })
        assertEquals("No name", rows[1].message)
        assertEquals("Bob has that phone number", rows[2].message)
        // The nameless row never went to the server
        assertEquals(listOf("Jane", "Bob", "Cara"), sent.single().second.map { it.name })
    }

    @Test
    fun matchingAColumnChecksAgainThenImportAdds() = runTest {
        val import = importer()
        import.open(ImportKind.CUSTOMERS, "Name,Phone\nJane,5550142\nBob,5550199")
        settle { import.preview.value != null }

        import.map(ImportField.PHONE, null)
        assertNull(import.preview.value)
        settle { import.preview.value != null }
        assertEquals("", sent.last().second.first().phone)

        var done = false
        import.import { done = true }
        settle { done }

        assertEquals("real", sent.last().first)
        assertEquals(1, imported)
        assertNull(import.sheet.value)
        settle { snackBar.currentSnackbarData != null }
        assertEquals("Added 1 customer", snackBar.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun anEmptyFileSaysSo() = runTest {
        assertEquals("There's nothing in that file under its headers", importer().open(ImportKind.INVENTORY, "Name\n"))
    }
}

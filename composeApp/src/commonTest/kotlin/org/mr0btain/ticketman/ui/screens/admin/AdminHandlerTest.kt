package org.mr0btain.ticketman.ui.screens.admin

import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
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
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.ui.screens.admin.components.AdminHandler
import kotlin.test.*
import kotlin.uuid.Uuid

class AdminHandlerTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val json = Json { ignoreUnknownKeys = true }

    /** Requests other than the audit log, as "METHOD path" to the body sent */
    private val requests = mutableListOf<Pair<String, String>>()
    private var writeStatus = HttpStatusCode.Accepted
    private var auditLeft = 0
    private var auditOffsets = mutableListOf<Long>()

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val body = (request.body as? TextContent)?.text ?: ""

        when {
            path == "/" -> respond("", HttpStatusCode.OK)
            path.endsWith("/audit/fetch") -> {
                auditOffsets += Regex("\"offset\":(\\d+)").find(body)!!.groupValues[1].toLong()
                val page = List(minOf(auditLeft, 50)) { entry() }
                auditLeft -= page.size
                respond(json.encodeToString(page), HttpStatusCode.OK, jsonHeaders)
            }
            path.startsWith("/api/admin/") -> respond("null", HttpStatusCode.OK, jsonHeaders)
            else -> {
                requests += "${request.method.value} $path" to body
                respond("", writeStatus)
            }
        }
    }

    private fun entry() = AuditEntry(Uuid.random(), null, "boss", AuditAction.UPDATE, "ticket", null, "", 0)

    private val snackBar = SnackbarHostState()
    private val usersChanged = mutableListOf<Uuid?>()
    private val sam = User(username = "sam", name = "Sam:Tech", accessLevel = AccessLevel.NORMAL, password = "old-hash")

    // Requests finish on the mock engine's threads, keep running the test's coroutines until done
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        fail("Timed out waiting")
    }

    private suspend fun TestScope.handler(): AdminHandler {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
        remote.setUrl("http://localhost")

        return AdminHandler(remote, backgroundScope, snackBar) { usersChanged += it }
    }

    @Test
    fun aNewUserIsSentWithTheirPassword() = runTest {
        val admin = handler()
        var closed = false

        admin.saveUser(sam, isNew = true, password = "new-pass") { closed = true }
        settle { snackBar.currentSnackbarData != null }

        val (call, body) = requests.single()
        assertEquals("POST /api/user/add", call)
        assertEquals("new-pass", json.decodeFromString<User>(body).password)
        assertTrue(closed)
        assertEquals(listOf<Uuid?>(sam.identifier), usersChanged)
        assertEquals("Added sam", snackBar.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun savingAnExistingUserNeverSendsAPassword() = runTest {
        val admin = handler()

        admin.saveUser(sam, isNew = false, password = "ignored") {}
        settle { snackBar.currentSnackbarData != null }

        val (call, body) = requests.single()
        assertEquals("POST /api/user/update", call)
        assertEquals("", json.decodeFromString<User>(body).password)
    }

    @Test
    fun aRefusedChangeShowsWhyAndKeepsTheDialogOpen() = runTest {
        writeStatus = HttpStatusCode.Conflict
        val admin = handler()
        var closed = false

        admin.saveUser(sam, isNew = true, password = "new-pass") { closed = true }
        settle { snackBar.currentSnackbarData != null }

        assertFalse(closed)
        assertTrue(usersChanged.isEmpty())
        assertNotEquals("Added sam", snackBar.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun deactivatingSendsTheStatus() = runTest {
        val admin = handler()

        admin.setUserActive(sam, active = false)
        settle { snackBar.currentSnackbarData != null }

        assertEquals(UserStatus.DEACTIVATED, json.decodeFromString<User>(requests.single().second).status)
        assertEquals("Deactivated sam", snackBar.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun theLowStockThresholdStaysInRange() = runTest {
        val admin = handler()

        admin.setLowStockThreshold(-4)
        assertEquals(AdminHandler.LOW_STOCK_RANGE.first, admin.lowStockThreshold.value)

        admin.setLowStockThreshold(1_000_000)
        assertEquals(AdminHandler.LOW_STOCK_RANGE.last, admin.lowStockThreshold.value)
    }

    @Test
    fun theAuditLogPagesUntilItRunsOut() = runTest {
        auditLeft = 70
        val admin = handler()

        admin.refreshAudit()
        settle { admin.auditEntries.value.size == 50 }
        assertFalse(admin.auditEndReached.value)

        admin.loadMoreAudit()
        settle { admin.auditEntries.value.size == 70 }
        assertTrue(admin.auditEndReached.value)

        // Nothing more is asked for once the end is reached
        admin.loadMoreAudit()
        runCurrent()
        assertEquals(listOf(0L, 50L), auditOffsets)
    }

    @Test
    fun clearForgetsEverythingLoaded() = runTest {
        auditLeft = 3
        val admin = handler()
        admin.refreshAudit()
        settle { admin.auditEntries.value.isNotEmpty() }

        admin.clear()

        assertTrue(admin.auditEntries.value.isEmpty())
        assertFalse(admin.auditEndReached.value)
        assertNull(admin.stats.value)
        assertTrue(admin.pending.value.isEmpty())
    }
}

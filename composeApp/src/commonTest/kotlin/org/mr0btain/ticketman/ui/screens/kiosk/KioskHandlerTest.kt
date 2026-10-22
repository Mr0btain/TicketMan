@file:OptIn(ExperimentalCoroutinesApi::class)

package org.mr0btain.ticketman.ui.screens.kiosk

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.ui.AppWindow
import org.mr0btain.ticketman.ui.screens.kiosk.components.KioskHandler
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class KioskHandlerTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val requests = mutableListOf<String>()
    private val member = StaffMember(Uuid.random(), "tech", "Terry Tech")

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"

        when (request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/" -> respond("", HttpStatusCode.OK)
            "/api/auth/pin" -> respond("""{"id":null,"accessToken":"pin","refreshToken":"pin-refresh"}""", HttpStatusCode.Accepted, jsonHeaders)
            "/api/auth/station/staff" -> respond("""[{"identifier":"${member.identifier}","username":"tech","name":"Terry Tech"}]""", HttpStatusCode.OK, jsonHeaders)
            else -> respond("", HttpStatusCode.Accepted)
        }
    }

    private val approvedKiosk = Station(
        identifier = Uuid.random(),
        name = "Front Counter",
        deviceId = "counter-1",
        status = StationStatus.APPROVED,
        requestedBy = "boss",
        requestedAt = 0,
        kioskEnabled = true,
        idleTimeoutSeconds = 30
    )

    private val station = MutableStateFlow<Station?>(null)
    private var clock = 0L
    private var signedIn = 0
    private var signedOut = 0
    private var admin = false
    private lateinit var remote: RemoteHandler

    // Requests finish on the mock engine's threads, keep running the test's coroutines until done
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
    }

    private suspend fun TestScope.handler(): KioskHandler {
        val storage = LocalStorage(InMemoryKeyValueStore()).apply {
            stationCredentials = StationCredentials(approvedKiosk.identifier, "secret")
        }
        remote = RemoteHandler(engine, storage)
        remote.setUrl("http://localhost")
        requests.clear()

        return KioskHandler(
            remoteHandler = remote,
            scope = backgroundScope,
            station = station,
            onSignedIn = { signedIn++ },
            onSignedOut = { signedOut++ },
            isAdmin = { admin },
            now = { clock }
        )
    }

    @AfterTest
    fun reset() = AppWindow.setFullscreen(false)

    @Test
    fun approvedKioskStationsStartLockedAndFullscreen() = runTest {
        val kiosk = handler()
        runCurrent()
        assertFalse(kiosk.active.value)

        station.value = approvedKiosk
        settle { kiosk.staff.value.isNotEmpty() }

        assertTrue(kiosk.active.value)
        assertTrue(kiosk.locked.value)
        assertTrue(AppWindow.fullscreen.value)
        assertFalse(remote.credentialFallback, "A locked kiosk shouldn't log the owner back in")
        assertEquals(listOf(member), kiosk.staff.value)
    }

    @Test
    fun stationsWithoutKioskModeStayNormal() = runTest {
        val kiosk = handler()

        station.value = approvedKiosk.copy(kioskEnabled = false)
        runCurrent()
        assertFalse(kiosk.active.value)

        station.value = approvedKiosk.copy(status = StationStatus.PENDING)
        runCurrent()
        assertFalse(kiosk.active.value)
    }

    @Test
    fun pinUnlocksAndIdleLocksAgain() = runTest {
        val kiosk = handler()
        station.value = approvedKiosk
        runCurrent()

        var error: String? = "not called"
        kiosk.unlock(member, "246813") { error = it }
        settle { !kiosk.locked.value }

        assertNull(error)
        assertEquals(1, signedIn)

        // Activity pushes the lock back
        clock += 20_000
        kiosk.recordActivity()
        clock += 20_000
        advanceTimeBy(2_000.milliseconds)
        assertFalse(kiosk.locked.value)

        // Left alone past the timeout
        clock += 31_000
        advanceTimeBy(2_000.milliseconds)
        assertTrue(kiosk.locked.value)
    }

    @Test
    fun onlyAnAdminCanLeave() = runTest {
        val kiosk = handler()
        station.value = approvedKiosk
        runCurrent()

        var error: String? = null
        kiosk.exit(member, "246813") { error = it }
        settle { error != null }

        assertEquals("Only an admin can leave kiosk mode", error)
        assertTrue(kiosk.active.value)
        assertTrue(kiosk.locked.value)

        admin = true
        error = "not called"
        kiosk.exit(member, "246813") { error = it }
        settle { error == null }

        assertFalse(kiosk.active.value)
        assertFalse(AppWindow.fullscreen.value)
        assertTrue(remote.credentialFallback)

        // The server is told, so the admin's PIN session stops ending with the idle lock
        assertTrue("POST /api/auth/kiosk/leave" in requests)
    }

    @Test
    fun someoneUsingTheKioskKeepsTheirSessionAlive() = runTest {
        val kiosk = handler()
        station.value = approvedKiosk.copy(idleTimeoutSeconds = 600)
        runCurrent()
        kiosk.unlock(member, "246813") { }
        settle { !kiosk.locked.value }
        requests.clear()

        // Nobody has touched it for over a minute, so there's nothing to keep alive
        clock += 120_000
        advanceTimeBy(2_000.milliseconds)
        assertFalse("GET /api/user/me" in requests)

        // Touched recently, the server hears about it
        kiosk.recordActivity()
        clock += 10_000
        advanceTimeBy(2_000.milliseconds)
        settle { "GET /api/user/me" in requests }
        assertEquals(1, requests.count { it == "GET /api/user/me" })

        // But only once a minute
        kiosk.recordActivity()
        clock += 10_000
        advanceTimeBy(2_000.milliseconds)
        runCurrent()
        assertEquals(1, requests.count { it == "GET /api/user/me" })
    }
}

package org.mr0btain.ticketman.ui.screens.locations

import androidx.compose.material3.SnackbarHostState
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.ALL_LOCATIONS
import org.mr0btain.ticketman.server.LOCATION_HEADER
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import org.mr0btain.ticketman.ui.screens.locations.components.LocationsHandler
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Where the device works: picked, remembered, fixed on a station, and sent with every request
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationsHandlerTest {
    private val main = Location(name = "Main Street", main = true)
    private val downtown = Location(code = "DT", name = "Downtown")
    private val headers = mutableListOf<String?>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val engine = MockEngine { request ->
        if (request.url.encodedPath != SERVER_PING_PATH) headers += request.headers[LOCATION_HEADER]
        when (request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/api/location" -> respond(Json.encodeToString(listOf(main, downtown)), HttpStatusCode.OK, jsonHeaders)
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private val storage = LocalStorage(InMemoryKeyValueStore())
    private var changes = 0

    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            advanceUntilIdle()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
    }

    private suspend fun TestScope.handler(): Pair<LocationsHandler, RemoteHandler> {
        val remote = RemoteHandler(engine, storage)
        remote.setUrl("http://localhost")
        return LocationsHandler(remote, storage, backgroundScope, SnackbarHostState(), onChanged = { changes++ }) to remote
    }

    @Test
    fun startsAtTheirHomeAndRemembersWhatTheyPick() = runTest {
        val (locations, remote) = handler()
        locations.start(User(homeLocation = downtown.identifier, accessLevel = AccessLevel.NORMAL), null)
        settle { locations.several.value }

        assertEquals(downtown.identifier, locations.working.value.identifier)
        assertTrue(locations.several.value)
        assertEquals(downtown.identifier.toString(), remote.location)
        assertEquals(downtown.identifier.toString(), headers.last(), "the list was asked for from downtown")

        locations.choose(main.identifier)
        assertEquals(main.identifier.toString(), remote.location)
        assertEquals(main.identifier.toString(), storage.workingLocation)
        assertEquals(1, changes)

        // Only admins look at every location
        locations.choose(null)
        assertEquals(main.identifier, locations.working.value.identifier)

        // Next time they're back where they picked
        val (again, _) = handler()
        again.start(User(homeLocation = downtown.identifier, accessLevel = AccessLevel.NORMAL), null)
        assertEquals(main.identifier, again.working.value.identifier)
    }

    @Test
    fun adminsCanLookAtEveryLocation() = runTest {
        val (locations, remote) = handler()
        locations.start(User(accessLevel = AccessLevel.ADMIN), null)
        settle { locations.all.value.isNotEmpty() }
        assertEquals(main.identifier, locations.working.value.identifier, "no home is the main one")

        locations.choose(null)
        assertTrue(locations.working.value.all)
        assertEquals(ALL_LOCATIONS, remote.location)
        assertEquals("All locations", locations.label())
    }

    @Test
    fun aStationStaysAtItsOwnLocation() = runTest {
        val (locations, remote) = handler()
        storage.workingLocation = main.identifier.toString()
        val station = Station(Uuid.random(), "Front", "device", StationStatus.APPROVED, "tech", 0, locationIdentifier = downtown.identifier)
        locations.start(User(accessLevel = AccessLevel.ADMIN), station)

        assertTrue(locations.fixed.value)
        assertEquals(downtown.identifier.toString(), remote.location)
        locations.choose(main.identifier)
        assertEquals(downtown.identifier.toString(), remote.location)
    }
}

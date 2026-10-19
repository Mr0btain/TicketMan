package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.API_VERSION_HEADER
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import kotlin.test.*

/**
 * The server says which API it speaks when pinged, and a different one is warned about
 */
class ApiVersionTest {
    private suspend fun mismatchWith(version: String?): String? {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                SERVER_PING_PATH -> respond(
                    SERVER_PING_BODY,
                    HttpStatusCode.OK,
                    if (version == null) headersOf() else headersOf(API_VERSION_HEADER, version)
                )
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
        assertEquals(ConnectionResult.CONNECTED, remote.setUrl("http://localhost"))
        return remote.apiMismatch
    }

    @Test
    fun theSameVersionIsFine() = runTest {
        assertNull(mismatchWith(API_VERSION.toString()))
    }

    @Test
    fun anOlderOrNewerServerIsWarnedAbout() = runTest {
        assertContains(mismatchWith(null)!!, "Update the server")
        assertContains(mismatchWith((API_VERSION - 1).toString())!!, "Update the server")
        assertContains(mismatchWith((API_VERSION + 1).toString())!!, "Update the app")
    }
}

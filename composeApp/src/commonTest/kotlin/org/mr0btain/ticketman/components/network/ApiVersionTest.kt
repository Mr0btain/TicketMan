package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.API_VERSION_HEADER
import org.mr0btain.ticketman.server.APP_VERSION
import org.mr0btain.ticketman.server.APP_VERSION_HEADER
import org.mr0btain.ticketman.server.AppVersion
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import kotlin.test.*

/**
 * The server says which API and release it is when pinged. A different API or a minor version apart is warned
 * about, a major version apart doesn't connect at all
 */
class ApiVersionTest {
    private fun remoteWith(apiVersion: String?, appVersion: String? = null): RemoteHandler {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                SERVER_PING_PATH -> respond(
                    SERVER_PING_BODY,
                    HttpStatusCode.OK,
                    headers {
                        apiVersion?.let { append(API_VERSION_HEADER, it) }
                        appVersion?.let { append(APP_VERSION_HEADER, it) }
                    }
                )
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        return RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
    }

    private suspend fun noticeWith(apiVersion: String?, appVersion: String? = null): VersionNotice? {
        val remote = remoteWith(apiVersion, appVersion)
        assertEquals(ConnectionResult.CONNECTED, remote.setUrl("http://localhost"))
        return remote.versionNotice
    }

    private fun version(text: String) = AppVersion.parse(text)!!

    @Test
    fun theSameVersionIsFine() = runTest {
        assertNull(noticeWith(API_VERSION.toString()))
        assertNull(noticeWith(API_VERSION.toString(), APP_VERSION))
    }

    @Test
    fun anOlderOrNewerApiIsWarnedAbout() = runTest {
        assertContains(noticeWith(null)!!.message, "Update the server")
        assertContains(noticeWith((API_VERSION - 1).toString())!!.message, "Update the server")
        assertContains(noticeWith((API_VERSION + 1).toString())!!.message, "Update the app")
        assertTrue(noticeWith((API_VERSION + 1).toString(), APP_VERSION)!!.message.contains("Update the app"))
        assertFalse(noticeWith(null)!!.blocking)
    }

    @Test
    fun aMajorVersionApartDoesNotConnect() = runTest {
        val current = AppVersion.current
        val newer = AppVersion(current.major + 1, 0, 0).toString()
        val remote = remoteWith(API_VERSION.toString(), newer)

        assertEquals(ConnectionResult.INCOMPATIBLE, remote.setUrl("http://localhost"))
        val notice = remote.versionNotice!!
        assertTrue(notice.blocking)
        assertFalse(notice.updateServer)
        assertContains(notice.message, "Update the app to $newer")

        // Nothing points at it, so nothing can load from it
        assertTrue(listOf(remote.customerRepo, remote.ticketRepo, remote.userRepo).all { it.serverUrl.isBlank() })
    }

    @Test
    fun everyRequestSaysTheAppVersionAndATurnedAwayOneIsReported() = runTest {
        val sent = mutableListOf<String?>()
        val engine = MockEngine { request ->
            sent += request.headers[APP_VERSION_HEADER]
            when (request.url.encodedPath) {
                SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK, headersOf(APP_VERSION_HEADER, APP_VERSION))
                else -> respond("", HttpStatusCode.UpgradeRequired)
            }
        }
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
        val rejected = mutableListOf<Unit>()
        val watching = launch { remote.versionRejected.collect { rejected += it } }
        testScheduler.runCurrent()

        remote.setUrl("http://localhost")
        remote.customerRepo.fetch(0, 10)
        testScheduler.runCurrent()

        assertTrue(sent.isNotEmpty())
        assertTrue(sent.all { it == APP_VERSION })
        assertTrue(rejected.isNotEmpty())
        watching.cancel()
    }

    @Test
    fun aMinorVersionApartSaysWhichToUpdate() {
        val older = versionNoticeOf(version("1.2.0"), API_VERSION, appVersion = version("1.4.1"))!!
        assertFalse(older.blocking)
        assertTrue(older.updateServer)
        assertContains(older.message, "Update the server to 1.4.1")

        val newer = versionNoticeOf(version("1.6.0"), API_VERSION, appVersion = version("1.4.1"))!!
        assertFalse(newer.blocking)
        assertFalse(newer.updateServer)
        assertContains(newer.message, "Update the app to 1.6.0")
    }

    @Test
    fun aMajorVersionApartBlocks() {
        val older = versionNoticeOf(version("1.9.0"), API_VERSION, appVersion = version("2.0.0"))!!
        assertTrue(older.blocking)
        assertTrue(older.updateServer)
        assertContains(older.message, "Update the server to 2.0.0")

        // Before 1.0 a minor version apart can break things too
        assertTrue(versionNoticeOf(version("0.8.0"), API_VERSION, appVersion = version("0.9.0"))!!.blocking)
    }

    @Test
    fun aPatchApartIsFine() {
        assertNull(versionNoticeOf(version("1.4.0"), API_VERSION, appVersion = version("1.4.3")))
    }
}

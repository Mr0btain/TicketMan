package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.AuthPaths
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import kotlin.test.*

/** The refresh token is all a device has to stay logged in with, and only the server turning it down ends a session */
class SessionResumeTest {
    private val requests = mutableListOf<String>()
    private var refreshStatus = HttpStatusCode.Accepted
    private val localStorage = LocalStorage(InMemoryKeyValueStore()).apply {
        username = "owner"
        accessToken = "old-access"
        refreshToken = "old-refresh"
    }

    private fun remote() = RemoteHandler(
        MockEngine { request ->
            val path = request.url.encodedPath
            requests += path
            when (path) {
                SERVER_PING_PATH -> respond(SERVER_PING_BODY)
                ApiRoutes.AUTH + AuthPaths.REFRESH -> respond(
                    content = if (refreshStatus.isSuccess()) """{"token":"new-access","refreshToken":"new-refresh"}""" else "",
                    status = refreshStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        },
        localStorage
    )

    private val login = ApiRoutes.AUTH

    @Test
    fun startingUpCarriesOnWithTheRefreshToken() = runTest {
        assertEquals(ConnectionResult.LOGGED_IN, remote().setUrl("http://localhost"))

        assertEquals("new-access", localStorage.accessToken)
        assertEquals("new-refresh", localStorage.refreshToken)
        assertFalse(login in requests, "It shouldn't log in, it has no password to do it with")
    }

    @Test
    fun aSessionTheServerEndedStaysEnded() = runTest {
        refreshStatus = HttpStatusCode.Unauthorized

        assertEquals(ConnectionResult.CONNECTED, remote().setUrl("http://localhost"))

        assertEquals("", localStorage.refreshToken)
        assertEquals("", localStorage.accessToken)
        assertFalse(login in requests)
    }

    @Test
    fun aServerErrorOnStartingUpKeepsTheSession() = runTest {
        refreshStatus = HttpStatusCode.InternalServerError

        assertEquals(ConnectionResult.CONNECTED, remote().setUrl("http://localhost"))

        assertEquals("old-refresh", localStorage.refreshToken)
    }

    @Test
    fun noRefreshTokenMeansLoggedOut() = runTest {
        localStorage.clearSession()

        assertEquals(ConnectionResult.CONNECTED, remote().setUrl("http://localhost"))

        assertFalse(ApiRoutes.AUTH + AuthPaths.REFRESH in requests)
    }

    @Test
    fun beingTurnedDownMidSessionAsksForALogin() = runTest {
        val remote = remote()
        remote.setUrl("http://localhost")
        refreshStatus = HttpStatusCode.Unauthorized

        val expired = async(start = CoroutineStart.UNDISPATCHED) { remote.authExpired.first() }
        assertNull(remote.refreshTokens())

        expired.await()
        assertEquals("", localStorage.refreshToken)
    }

    @Test
    fun aFailedRefreshMidSessionDoesntLogOut() = runTest {
        val remote = remote()
        remote.setUrl("http://localhost")
        val token = localStorage.refreshToken
        refreshStatus = HttpStatusCode.InternalServerError

        assertNull(remote.refreshTokens())

        assertEquals(token, localStorage.refreshToken, "A server having a bad moment shouldn't end the session")
    }
}

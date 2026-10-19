package org.mr0btain.ticketman.ui.screens.login

import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.screens.login.components.AccountSetup
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.login.components.SessionHandler
import kotlin.test.*

class SessionHandlerTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val requests = mutableListOf<String>()

    private var reachable = true
    private var loginBody = """{"id":null,"accessToken":"access","refreshToken":"refresh"}"""
    private var loginStatus = HttpStatusCode.Accepted
    private var me = User(username = "sam", name = "Sam:Tech", accessLevel = AccessLevel.NORMAL)

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"
        if (!reachable) throw IOException("Server is down")

        // A server with HTTP_MODE=redirect
        if (request.url.host == "shop.local" && request.url.protocol == URLProtocol.HTTP) {
            return@MockEngine respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://shop.local:8443${request.url.encodedPath}"))
        }

        when (request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/" -> respond("", HttpStatusCode.OK)
            "/api/auth" -> respond(loginBody, loginStatus, jsonHeaders)
            "/api/user/me" -> respond(Json.encodeToString(me), HttpStatusCode.OK, jsonHeaders)
            else -> respond("", HttpStatusCode.Accepted)
        }
    }

    private var loggedIn = 0
    private var loggedOut = 0
    private var movedTo: String? = null

    // Requests finish on the mock engine's threads, keep running the test's coroutines until done
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        fail("Timed out waiting")
    }

    private suspend fun TestScope.handler(connect: Boolean = true): SessionHandler {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))

        return SessionHandler(
            remoteHandler = remote,
            localStorage = LocalStorage(InMemoryKeyValueStore()),
            scope = backgroundScope,
            onLoggedIn = { loggedIn++ },
            onLoggedOut = { loggedOut++ },
            onServerMoved = { movedTo = it }
        ).also { session ->
            if (connect) session.connect("http://localhost")
            requests.clear()
        }
    }

    @Test
    fun connectingWithoutAStoredLoginLeavesYouLoggedOut() = runTest {
        val session = handler()

        assertTrue(session.serverConnected.value)
        assertEquals(LoginState.LOGGED_OUT, session.loginState.value)
        assertNull(session.currentUser.value)
    }

    @Test
    fun aServerThatRedirectsToHttpsMovesTheSavedAddress() = runTest {
        val session = handler(connect = false)

        session.connect("http://shop.local:8080")
        runCurrent()

        assertTrue(session.serverConnected.value)
        assertTrue(session.connectionSecure.value, "Now on the https:// address")
        assertEquals("https://shop.local:8443", movedTo)
    }

    @Test
    fun anAddressThatDoesntRedirectStaysPut() = runTest {
        handler()
        assertNull(movedTo)
    }

    @Test
    fun anUnreachableServerIsReported() = runTest {
        reachable = false
        val session = handler()

        assertFalse(session.serverConnected.value)
        assertEquals(LoginState.LOGGED_OUT, session.loginState.value)
    }

    @Test
    fun blankCredentialsFailWithoutAsking() = runTest {
        val session = handler()

        session.login("  ", "pass")
        session.login("sam", "")

        assertEquals(LoginState.FAILED, session.loginState.value)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun loggingInLoadsTheUserAndWhatTheyCanDo() = runTest {
        val session = handler()

        session.login(" sam ", "pass")
        settle { loggedIn == 1 }
        runCurrent()

        assertEquals(LoginState.LOGGED_IN, session.loginState.value)
        assertEquals(me, session.currentUser.value)
        assertTrue(session.canEdit.value)
        assertTrue(session.canEditNow())
        assertFalse(session.isAdmin.value)
        assertFalse(session.showLogin.value)
    }

    @Test
    fun restrictedUsersCanOnlyLook() = runTest {
        me = me.copy(accessLevel = AccessLevel.RESTRICTED)
        val session = handler()

        session.login("sam", "pass")
        settle { loggedIn == 1 }
        runCurrent()

        assertFalse(session.canEdit.value)
        assertFalse(session.canEditNow())
    }

    @Test
    fun adminsCanReachTheAdminScreen() = runTest {
        me = me.copy(accessLevel = AccessLevel.ADMIN)
        val session = handler()

        session.login("sam", "pass")
        settle { loggedIn == 1 }
        runCurrent()

        assertTrue(session.isAdmin.value)
        assertTrue(session.isAdminNow())
    }

    @Test
    fun aWrongPasswordSaysSo() = runTest {
        loginStatus = HttpStatusCode.Unauthorized
        loginBody = ""
        val session = handler()

        session.login("sam", "wrong")
        settle { session.loginState.value == LoginState.FAILED }

        assertEquals("Wrong username, password or setup code.", session.loginMessage.value)
        assertEquals(0, loggedIn)
    }

    @Test
    fun aServerThatGoesDownIsNotAWrongPassword() = runTest {
        val session = handler()
        reachable = false

        session.login("sam", "pass")
        settle { session.loginState.value == LoginState.FAILED }

        assertEquals("Couldn't reach the server", session.loginMessage.value)
    }

    @Test
    fun aServerErrorSaysSo() = runTest {
        loginStatus = HttpStatusCode.InternalServerError
        loginBody = ""
        val session = handler()

        session.login("sam", "pass")
        settle { session.loginState.value == LoginState.FAILED }

        assertEquals("The server had an error", session.loginMessage.value)
    }

    @Test
    fun anAccountWaitingForApprovalIsNotLoggedIn() = runTest {
        loginBody = """{"id":null,"accessToken":null,"refreshToken":null,"status":"PENDING_APPROVAL"}"""
        val session = handler()

        session.login("sam", "pass")
        settle { session.loginMessage.value != null }

        assertEquals(LoginState.LOGGED_OUT, session.loginState.value)
        assertEquals("Your account is waiting for an admin to approve it.", session.loginMessage.value)
        assertEquals(0, loggedIn)
    }

    @Test
    fun anInviteCodeStartsSetup() = runTest {
        loginBody = """{"id":null,"accessToken":null,"refreshToken":null,"status":"INVITE_SETUP","setupToken":"setup"}"""
        val session = handler()

        session.login("newtech", "code")
        settle { session.accountSetup.value != null }

        assertEquals(AccountSetup.Invite("newtech", "setup"), session.accountSetup.value)
        assertEquals(LoginState.LOGGED_OUT, session.loginState.value)
    }

    @Test
    fun anApprovedAccountHasToFinishSetupFirst() = runTest {
        loginBody = """{"id":null,"accessToken":"access","refreshToken":"refresh","status":"NEEDS_SETUP"}"""
        me = me.copy(needsSetup = true)
        val session = handler()

        session.login("sam", "pass")
        settle { session.accountSetup.value != null }

        assertIs<AccountSetup.Approved>(session.accountSetup.value)
        assertEquals(0, loggedIn)
        assertFalse(session.canEditNow())
    }

    @Test
    fun loggingOutEndsTheSessionAndForgetsTheUser() = runTest {
        val session = handler()
        session.login("sam", "pass")
        settle { loggedIn == 1 }

        session.logout()
        settle { loggedOut == 1 }
        runCurrent()

        assertEquals(LoginState.LOGGED_OUT, session.loginState.value)
        assertNull(session.currentUser.value)
        assertFalse(session.canEdit.value)
        assertTrue(requests.any { it.endsWith("/api/auth/logout") })
    }

    @Test
    fun anExpiredSessionAsksForALoginAgain() = runTest {
        val session = handler()
        session.login("sam", "pass")
        settle { loggedIn == 1 }

        session.expired()

        assertTrue(session.showLogin.value)
        assertNull(session.currentUser.value)
        assertEquals(LoginState.LOGGED_OUT, session.loginState.value)
    }

    @Test
    fun aDeviceThatIsntAStationHasNone() = runTest {
        val session = handler()

        session.refreshStation()

        assertNull(session.station.value)
    }
}

package org.mr0btain.ticketman.components.network.user

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import kotlin.test.*
import kotlin.uuid.Uuid

class ServerUserClientTest {
    private val store = InMemoryKeyValueStore()
    private val localStorage = LocalStorage(store)
    private val requests = mutableListOf<String>()

    private var pinAccepted = true
    private val station = StationCredentials(Uuid.parse("00000000-0000-7000-8000-0000000000aa"), "secret")

    // What the login route answers with, tests swap it for other outcomes
    private var loginBody = """{"id":null,"accessToken":"access","refreshToken":"refresh"}"""

    private val engine = MockEngine { request ->
        requests += request.url.encodedPath

        when (request.url.encodedPath) {
            "/api/auth" -> respond(
                content = loginBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )

            "/api/auth/request-account" -> respond("Username is already taken", HttpStatusCode.Conflict)

            "/api/auth/pin" -> if (pinAccepted) respond(
                content = """{"id":null,"accessToken":"pin-access","refreshToken":"pin-refresh"}""",
                status = HttpStatusCode.Accepted,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            ) else respond("Wrong PIN, 4 tries left", HttpStatusCode.Unauthorized)

            "/api/auth/station/staff" -> respond(
                content = """[{"identifier":"00000000-0000-7000-8000-000000000001","username":"tech","name":"terry:tech"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )

            "/api/auth/setup/invite" -> respond(
                content = """{"id":null,"accessToken":"access","refreshToken":"refresh"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )

            else -> respond("", HttpStatusCode.OK)
        }
    }

    private val client = ServerUserClient(createUserHttpClient(engine), localStorage).apply {
        changeServerUrl("http://localhost")
    }

    @Test
    fun loginWithBlankCredentialsSkipsRequest() = runTest {
        assertEquals(LoginOutcome.Failed, client.login("", ""))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun loginStoresTokensInMatchingFields() = runTest {
        assertIs<LoginOutcome.LoggedIn>(client.login("user", "pass"))

        assertEquals("access", localStorage.accessToken)
        assertEquals("refresh", localStorage.refreshToken)
    }

    @Test
    fun loginSavesCredentialsWhenRemembered() = runTest {
        localStorage.rememberLogin = true

        assertIs<LoginOutcome.LoggedIn>(client.login("user", "pass"))

        assertEquals("user", localStorage.username)
        assertEquals("pass", localStorage.password)
    }

    @Test
    fun loginDoesNotSaveCredentialsWhenNotRemembered() = runTest {
        localStorage.rememberLogin = false

        assertIs<LoginOutcome.LoggedIn>(client.login("user", "pass"))

        assertEquals("", localStorage.username)
        assertEquals("", localStorage.password)
        assertFalse(store.values.containsKey("username"))
        assertFalse(store.values.containsKey("password"))
    }

    @Test
    fun loginReportsAccountsWaitingForApproval() = runTest {
        loginBody = """{"id":null,"accessToken":null,"refreshToken":null,"status":"PENDING_APPROVAL"}"""

        assertEquals(LoginOutcome.PendingApproval, client.login("user", "pass"))
        assertEquals("", localStorage.accessToken)
    }

    @Test
    fun loginWithASetupCodeHandsBackTheSetupToken() = runTest {
        loginBody = """{"id":null,"accessToken":null,"refreshToken":null,"status":"INVITE_SETUP","setupToken":"setup"}"""

        assertEquals(LoginOutcome.InviteSetup("user", "setup"), client.login("user", "CODE1234"))

        // The code isn't a password, so it shouldn't be remembered
        assertEquals("", localStorage.password)
    }

    @Test
    fun loginFlagsAccountsThatNeedSetup() = runTest {
        loginBody = """{"id":null,"accessToken":"access","refreshToken":"refresh","status":"NEEDS_SETUP"}"""

        assertEquals(LoginOutcome.LoggedIn(needsSetup = true), client.login("user", "pass"))
        assertEquals("access", localStorage.accessToken)
    }

    @Test
    fun finishingAnInviteStoresTheSession() = runTest {
        val result = client.completeInviteSetup("user", InviteSetupRequest("setup", "new_password", "user:name"))

        assertIs<Result.Success<Unit>>(result)
        assertEquals("access", localStorage.accessToken)
        assertTrue("/api/auth/setup/invite" in requests)
    }

    @Test
    fun accountRequestPassesOnTheServersReason() = runTest {
        val result = client.requestAccount(AccountRequest("taken", "password1"))

        assertIs<Result.Error<*>>(result)
        assertEquals("Username is already taken", (result as Result.Error).error.message)
    }

    @Test
    fun pinLoginSwapsTheTokensButNotTheSavedLogin() = runTest {
        localStorage.username = "owner"
        localStorage.password = "owner_pass"

        val result = client.pinLogin(station, Uuid.random(), "246813")

        assertIs<Result.Success<Unit>>(result)
        assertEquals("pin-access", localStorage.accessToken)
        assertEquals("owner", localStorage.username)
        assertEquals("owner_pass", localStorage.password)
    }

    @Test
    fun wrongPinPassesOnTheTriesLeft() = runTest {
        pinAccepted = false

        val result = client.pinLogin(station, Uuid.random(), "000001")

        assertIs<Result.Error<*>>(result)
        assertEquals("Wrong PIN, 4 tries left", (result as Result.Error).error.message)
        assertEquals("", localStorage.accessToken)
    }

    @Test
    fun stationStaffList() = runTest {
        val staff = client.stationStaff(station)

        assertIs<Result.Success<List<StaffMember>>>(staff)
        assertEquals("tech", staff.data.single().username)
    }
}

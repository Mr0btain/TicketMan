package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import kotlin.test.*

/**
 * Covers the login -> authenticated request -> refresh flow the client relies on
 */
class AuthTest {
    private val dataDir = createTestDataDir()
    private val user = User(username = "test_user", password = "test_pass", accessLevel = AccessLevel.ADMIN)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()

        // Database is connected once the module has run
        UserDatabase().add(user)
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, accessToken: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            accessToken?.let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.login(password: String = user.password): HttpResponse =
        postJson("/api/auth", Json.encodeToString(LoginRequest(user.username, password)))

    @Test
    fun loginReturnsTokens() = testApplication {
        setup()

        val response = login()
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)

        val auth = Json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals(user.identifier, auth.id)
        assertFalse(auth.accessToken.isNullOrBlank())
        assertFalse(auth.refreshToken.isNullOrBlank())
    }

    @Test
    fun loginWithWrongPasswordIsRejected() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Unauthorized, login(password = "wrong").status)
    }

    @Test
    fun refreshReturnsNewAccessToken() = testApplication {
        setup()
        val auth = Json.decodeFromString<AuthResponse>(login().bodyAsText())

        val response = postJson("/api/auth/refresh", Json.encodeToString(RefreshTokenRequest(auth.refreshToken!!)))
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)

        val refreshed = Json.decodeFromString<RefreshTokenResponse>(response.bodyAsText())
        assertFalse(refreshed.token.isBlank())
    }

    @Test
    fun refreshWithUnknownTokenIsRejected() = testApplication {
        setup()

        val response = postJson("/api/auth/refresh", Json.encodeToString(RefreshTokenRequest("not-a-token")))
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun protectedRouteRequiresAccessToken() = testApplication {
        setup()
        val body = Json.encodeToString(PagedRequest(offset = 0, itemsPerPage = 10))

        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/data/customer/fetch", body).status)

        val auth = Json.decodeFromString<AuthResponse>(login().bodyAsText())
        assertEquals(HttpStatusCode.OK, postJson("/api/data/customer/fetch", body, auth.accessToken).status)
    }

    @Test
    fun pagerFetchRoutesMatchClient() = testApplication {
        setup()
        val auth = Json.decodeFromString<AuthResponse>(login().bodyAsText())
        val body = Json.encodeToString(PagedRequest(offset = 0, itemsPerPage = 10))

        // Same routes the client's *RemoteRepository classes use
        listOf("api/data/customer", "api/data/ticket", "api/data/inventory", "api/user").forEach { route ->
            val response = postJson("/$route/fetch", body, auth.accessToken)
            assertEquals(HttpStatusCode.OK, response.status, "POST /$route/fetch")
        }

        // The test user should come back from the user pager route
        val users = postJson("/api/user/fetch", body, auth.accessToken).bodyAsText()
        assertTrue(users.contains(user.username), "User fetch didn't include the test user: $users")
    }
}

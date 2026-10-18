package org.mr0btain.ticketman

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.time.Instant
import kotlin.test.*

/**
 * Which bearer tokens get in: only ones signed by this server, unexpired, for a live session of the user they name
 */
class JwtTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val other = User(username = "other", password = "other_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(): String {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(tech)
            add(other)
        }

        return login(tech)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    /** A token like the server's, with the parts a test wants changed */
    private fun token(
        identifier: String = tech.identifier.toString(),
        sessionId: String?,
        expiresAt: Instant = Instant.now().plusSeconds(600),
        secret: String = "test-secret",
        audience: String = "test-audience"
    ): String = JWT.create()
        .withAudience(audience)
        .withIssuer("http://localhost")
        .withClaim("identifier", identifier)
        .apply { if (sessionId != null) withClaim(JwtService.SESSION_CLAIM, sessionId) }
        .withExpiresAt(expiresAt)
        .sign(Algorithm.HMAC256(secret))

    private fun sessionOf(token: String): String = JWT.decode(token).getClaim(JwtService.SESSION_CLAIM).asString()

    private suspend fun ApplicationTestBuilder.status(token: String) =
        client.get("/api/user/me") { bearerAuth(token) }.status

    @Test
    fun theLoginsOwnTokenGetsIn() = testApplication {
        val access = setup()

        assertEquals(HttpStatusCode.OK, status(access))
        assertEquals(HttpStatusCode.OK, status(token(sessionId = sessionOf(access))))
    }

    @Test
    fun anExpiredTokenIsRefused() = testApplication {
        val access = setup()

        assertEquals(HttpStatusCode.Unauthorized, status(token(sessionId = sessionOf(access), expiresAt = Instant.now().minusSeconds(60))))
    }

    @Test
    fun aTokenSignedElsewhereIsRefused() = testApplication {
        val access = setup()

        assertEquals(HttpStatusCode.Unauthorized, status(token(sessionId = sessionOf(access), secret = "someone-elses-secret")))
        assertEquals(HttpStatusCode.Unauthorized, status(token(sessionId = sessionOf(access), audience = "another-app")))
    }

    @Test
    fun aTokenNeedsASessionOfTheUserItNames() = testApplication {
        val access = setup()
        val otherAccess = login(other)

        // No session at all, a made up one, and someone else's
        assertEquals(HttpStatusCode.Unauthorized, status(token(sessionId = null)))
        assertEquals(HttpStatusCode.Unauthorized, status(token(sessionId = "not-a-session")))
        assertEquals(HttpStatusCode.Unauthorized, status(token(sessionId = sessionOf(otherAccess))))
        assertEquals(HttpStatusCode.OK, status(access))
    }

    @Test
    fun aSetupTokenCantBeUsedToLogIn() = testApplication {
        setup()
        val setupToken = JWT.create()
            .withAudience("test-audience")
            .withIssuer("http://localhost")
            .withClaim("identifier", tech.identifier.toString())
            .withClaim("purpose", "invite-setup")
            .withExpiresAt(Instant.now().plusSeconds(600))
            .sign(Algorithm.HMAC256("test-secret"))

        assertEquals(HttpStatusCode.Unauthorized, status(setupToken))
    }

    @Test
    fun aRemovedUsersTokenStopsWorking() = testApplication {
        val access = setup()

        UserDatabase().remove(tech.identifier)

        assertEquals(HttpStatusCode.Unauthorized, status(access))
    }
}

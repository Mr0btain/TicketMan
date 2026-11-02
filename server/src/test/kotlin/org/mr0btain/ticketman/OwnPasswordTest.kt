package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.ChangePasswordRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PasswordResetRequest
import org.mr0btain.ticketman.server.request.SetPinRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginStatus
import kotlin.test.*

/** People choose and change their own passwords, and an admin never gets to know one someone keeps using */
class OwnPasswordTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
    }

    private suspend fun ApplicationTestBuilder.tryLogin(username: String, password: String, device: String = "desk") =
        client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(username, password, device, device)))
        }

    private suspend fun ApplicationTestBuilder.login(username: String, password: String, device: String = "desk"): AuthResponse {
        val response = tryLogin(username, password, device)
        assertEquals(HttpStatusCode.Accepted, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.change(token: String, current: String, new: String) =
        post("/api/user/me/password", json.encodeToString(ChangePasswordRequest(current, new)), token)

    private suspend fun ApplicationTestBuilder.tickets(token: String) =
        client.get("/api/data/ticket/fetch") { bearerAuth(token) }.status

    @Test
    fun anyoneCanChangeTheirOwn() = testApplication {
        setup()
        val here = login("tech", "tech_pass", "desk").accessToken!!
        val other = login("tech", "tech_pass", "phone").accessToken!!

        assertEquals(HttpStatusCode.BadRequest, change(here, "wrong_pass", "new_password").status)
        assertEquals(HttpStatusCode.BadRequest, change(here, "tech_pass", "short").status)
        assertEquals(HttpStatusCode.BadRequest, change(here, "tech_pass", "tech_pass").status)

        assertEquals(HttpStatusCode.Accepted, change(here, "tech_pass", "new_password").status)

        assertEquals(HttpStatusCode.Unauthorized, tryLogin("tech", "tech_pass").status)
        login("tech", "new_password", "tablet")

        // This device carries on, the others are logged out
        assertEquals(HttpStatusCode.OK, tickets(here))
        assertEquals(HttpStatusCode.Unauthorized, tickets(other))
    }

    @Test
    fun guessingThroughItIsLimited() = testApplication {
        setup()
        val token = login("tech", "tech_pass").accessToken!!

        repeat(5) { assertEquals(HttpStatusCode.BadRequest, change(token, "guess_$it", "new_password").status) }

        assertEquals(HttpStatusCode.TooManyRequests, change(token, "tech_pass", "new_password").status)
        val pin = post("/api/user/me/pin", json.encodeToString(SetPinRequest("tech_pass", "246813")), token)
        assertEquals(HttpStatusCode.TooManyRequests, pin.status)
    }

    @Test
    fun guessesSentTogetherAreLimitedToo() = testApplication {
        setup()
        val token = login("tech", "tech_pass").accessToken!!

        val statuses = coroutineScope {
            (1..12).map { n -> async(Dispatchers.Default) { change(token, "guess_$n", "new_password").status } }.awaitAll()
        }

        assertEquals(5, statuses.count { it == HttpStatusCode.BadRequest }, statuses.toString())
        assertEquals(7, statuses.count { it == HttpStatusCode.TooManyRequests }, statuses.toString())
    }

    @Test
    fun theRightPasswordDoesntUseUpTheLimit() = testApplication {
        setup()
        val token = login("tech", "tech_pass").accessToken!!

        // Right each time, the new one just isn't allowed
        repeat(7) { assertEquals(HttpStatusCode.BadRequest, change(token, "tech_pass", "short").status) }

        repeat(4) { assertEquals(HttpStatusCode.BadRequest, change(token, "guess_$it", "new_password").status) }
        assertEquals(HttpStatusCode.Accepted, change(token, "tech_pass", "new_password").status)
    }

    @Test
    fun wrongGuessesAtThePinScreenCountToo() = testApplication {
        setup()
        val token = login("tech", "tech_pass").accessToken!!

        repeat(5) { assertEquals(HttpStatusCode.BadRequest, post("/api/user/me/pin", json.encodeToString(SetPinRequest("guess_$it", "246813")), token).status) }

        assertEquals(HttpStatusCode.TooManyRequests, change(token, "tech_pass", "new_password").status)
    }

    @Test
    fun aPasswordFromAnAdminHasToBeReplaced() = testApplication {
        setup()
        val boss = login("boss", "boss_pass").accessToken!!
        val employee = User(username = "newbie", password = "from_the_boss", accessLevel = AccessLevel.NORMAL)
        assertEquals(HttpStatusCode.Created, post("/api/user/add", json.encodeToString(employee), boss).status)

        val first = login("newbie", "from_the_boss")
        assertEquals(LoginStatus.NEEDS_SETUP, first.status)
        val token = first.accessToken!!

        // Nothing works but changing it, not even with what they send saying otherwise
        assertEquals(HttpStatusCode.Forbidden, tickets(token))
        val me = json.decodeFromString<User>(client.get("/api/user/me") { bearerAuth(token) }.bodyAsText())
        assertTrue(me.mustChangePassword)

        assertEquals(HttpStatusCode.Accepted, change(token, "from_the_boss", "my_own_password").status)

        assertEquals(HttpStatusCode.OK, tickets(token))
        assertEquals(LoginStatus.OK, login("newbie", "my_own_password", "phone").status)
    }

    @Test
    fun aResetEndsTheirSessionsAndTheyChooseAgain() = testApplication {
        setup()
        val boss = login("boss", "boss_pass").accessToken!!
        val before = login("tech", "tech_pass").accessToken!!

        val reset = post("/api/user/password/${tech.identifier}", json.encodeToString(PasswordResetRequest("temporary_pass")), boss)
        assertEquals(HttpStatusCode.Accepted, reset.status)
        assertEquals(HttpStatusCode.Unauthorized, tickets(before))

        val after = login("tech", "temporary_pass")
        assertEquals(LoginStatus.NEEDS_SETUP, after.status)
        assertEquals(HttpStatusCode.Forbidden, tickets(after.accessToken!!))
    }

    @Test
    fun anAdminCantResetTheirOwnWithoutTheCurrentOne() = testApplication {
        setup()
        val boss = login("boss", "boss_pass").accessToken!!

        val reset = post("/api/user/password/${admin.identifier}", json.encodeToString(PasswordResetRequest("taken_over_pass")), boss)
        assertEquals(HttpStatusCode.Conflict, reset.status)

        login("boss", "boss_pass", "phone")
    }
}

package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.InviteDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.*
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

/**
 * Account requests, approval, invites and first login setup
 */
class AccountTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)

    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(): UserDatabase {
        configureTestServer(dataDir)
        startApplication()
        val users = UserDatabase().apply { add(admin) }
        adminToken = json.decodeFromString<AuthResponse>(login(admin.username, admin.password).bodyAsText()).accessToken!!
        return users
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            token?.let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.login(username: String, password: String) =
        postJson("/api/auth", json.encodeToString(LoginRequest(username, password)))

    private suspend fun ApplicationTestBuilder.loginResult(username: String, password: String): AuthResponse {
        val response = login(username, password)
        assertEquals(HttpStatusCode.Accepted, response.status, "Login for $username")
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.request(username: String, password: String = "new_password") =
        postJson("/api/auth/request-account", json.encodeToString(AccountRequest(username, password, name = "New Person")))

    private suspend fun ApplicationTestBuilder.pending(): List<PendingUserResponse> =
        json.decodeFromString(client.get("/api/user/pending") { bearerAuth(adminToken) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.invite(username: String): InviteResponse {
        val response = postJson("/api/user/invite", json.encodeToString(InviteRequest(username, accessLevel = AccessLevel.NORMAL)), adminToken)
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    // ------------------------------------------ Requests ------------------------------------------

    @Test
    fun requestedAccountWaitsForApprovalThenNeedsSetup() = testApplication {
        val users = setup()
        assertEquals(HttpStatusCode.Accepted, request("newbie").status)

        // Right password says it's waiting, wrong password is a normal failure
        assertEquals(LoginStatus.PENDING_APPROVAL, loginResult("newbie", "new_password").status)
        assertNull(loginResult("newbie", "new_password").accessToken)
        assertEquals(HttpStatusCode.Unauthorized, login("newbie", "wrong_password").status)

        val waiting = pending().single()
        assertEquals("newbie", waiting.user.username)
        assertEquals(PendingKind.REQUEST, waiting.kind)
        assertEquals("", waiting.user.password)

        val approve = postJson("/api/user/approve/${waiting.user.identifier}", json.encodeToString(ApproveRequest(AccessLevel.NORMAL)), adminToken)
        assertEquals(HttpStatusCode.Accepted, approve.status)

        // First login has tokens but has to finish setup before using anything
        val first = loginResult("newbie", "new_password")
        assertEquals(LoginStatus.NEEDS_SETUP, first.status)
        val token = first.accessToken!!

        assertEquals(HttpStatusCode.OK, client.get("/api/user/me") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/data/ticket/fetch?limit=5") { bearerAuth(token) }.status)

        // A PIN is required
        val noPin = postJson("/api/user/setup", json.encodeToString(AccountSetupRequest(name = "New Person")), token)
        assertEquals(HttpStatusCode.BadRequest, noPin.status)

        val setup = postJson("/api/user/setup", json.encodeToString(AccountSetupRequest(name = "New Person", email = "n@shop.test", pin = "246813")), token)
        assertEquals(HttpStatusCode.Accepted, setup.status)

        assertEquals(HttpStatusCode.OK, client.get("/api/data/ticket/fetch?limit=5") { bearerAuth(token) }.status)
        assertEquals(LoginStatus.OK, loginResult("newbie", "new_password").status)
        assertEquals("n@shop.test", users.getByUsername("newbie")!!.email)
        assertEquals(AccessLevel.NORMAL, users.getByUsername("newbie")!!.accessLevel)
        assertTrue(users.getByUsername("newbie")!!.hasPin)

        // Only once
        assertEquals(HttpStatusCode.Conflict, postJson("/api/user/setup", json.encodeToString(AccountSetupRequest(name = "x", pin = "246813")), token).status)
    }

    @Test
    fun requestsAreValidated() = testApplication {
        setup()

        assertEquals(HttpStatusCode.BadRequest, request("no spaces").status)
        assertEquals(HttpStatusCode.BadRequest, request("ab").status)
        assertEquals(HttpStatusCode.BadRequest, request("okname", password = "short").status)
        assertEquals(HttpStatusCode.Conflict, request(admin.username).status)
    }

    @Test
    fun requestsAreRateLimited() = testApplication {
        setup()

        repeat(5) { assertEquals(HttpStatusCode.Accepted, request("person$it").status) }
        assertEquals(HttpStatusCode.TooManyRequests, request("person5").status)
    }

    @Test
    fun rejectingRemovesTheRequest() = testApplication {
        val users = setup()
        request("newbie")
        val waiting = pending().single()

        val response = client.delete("/api/user/reject/${waiting.user.identifier}") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.Accepted, response.status)

        assertNull(users.getByUsername("newbie"))
        assertEquals(HttpStatusCode.Unauthorized, login("newbie", "new_password").status)

        // Active users can't be "rejected"
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/user/reject/${admin.identifier}") { bearerAuth(adminToken) }.status)
    }

    @Test
    fun onlyAdminsSeePendingUsers() = testApplication {
        val users = setup()
        val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL).also { users.add(it) }
        val techToken = loginResult(tech.username, tech.password).accessToken!!

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/user/pending") { bearerAuth(techToken) }.status)
    }

    // ------------------------------------------ Invites ------------------------------------------

    @Test
    fun invitedUserSetsUpWithTheirCode() = testApplication {
        val users = setup()
        val invite = invite("invited")

        assertEquals(InviteDatabase.CODE_LENGTH, invite.code.length)
        assertEquals(PendingKind.INVITE, pending().single().kind)
        assertNotNull(pending().single().inviteExpiresAt)

        // Logging in with the code gives a setup token rather than a session
        assertEquals(HttpStatusCode.Unauthorized, login("invited", "WRONGCDE").status)
        val viaCode = loginResult("invited", invite.code.lowercase())
        assertEquals(LoginStatus.INVITE_SETUP, viaCode.status)
        assertNull(viaCode.accessToken)
        val setupToken = viaCode.setupToken!!

        // The setup token can't be used for anything else
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/user/me") { bearerAuth(setupToken) }.status)

        val finish = postJson(
            "/api/auth/setup/invite",
            json.encodeToString(InviteSetupRequest(setupToken, password = "my_new_password", name = "Invited Person", pin = "246813"))
        )
        assertEquals(HttpStatusCode.Accepted, finish.status)
        val session = json.decodeFromString<AuthResponse>(finish.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/api/user/me") { bearerAuth(session.accessToken!!) }.status)

        val activated = users.getByUsername("invited")!!
        assertEquals(UserStatus.ACTIVE, activated.status)
        assertEquals(AccessLevel.NORMAL, activated.accessLevel)
        assertFalse(activated.needsSetup)

        // Code is spent, the new password works
        assertEquals(HttpStatusCode.Unauthorized, login("invited", invite.code).status)
        assertEquals(LoginStatus.OK, loginResult("invited", "my_new_password").status)
        assertTrue(pending().isEmpty())
    }

    @Test
    fun expiredOrRenewedCodesStopWorking() = testApplication {
        setup()
        val invite = invite("invited")

        // A renewed code replaces the old one
        val renewed = json.decodeFromString<InviteResponse>(
            client.post("/api/user/invite/${invite.user.identifier}/renew") { bearerAuth(adminToken) }.bodyAsText()
        )
        assertEquals(HttpStatusCode.Unauthorized, login("invited", invite.code).status)
        assertEquals(LoginStatus.INVITE_SETUP, loginResult("invited", renewed.code).status)

        // Expired codes don't work
        val (expiredCode, _) = InviteDatabase().create(invite.user.identifier, "test", lifetime = (-1).hours)!!
        assertEquals(HttpStatusCode.Unauthorized, login("invited", expiredCode).status)
    }

    @Test
    fun invitesCantBeApprovedAndNeedAValidUsername() = testApplication {
        setup()
        val invite = invite("invited")

        val approve = postJson("/api/user/approve/${invite.user.identifier}", json.encodeToString(ApproveRequest(AccessLevel.ADMIN)), adminToken)
        assertEquals(HttpStatusCode.Conflict, approve.status)

        val bad = postJson("/api/user/invite", json.encodeToString(InviteRequest("bad name")), adminToken)
        assertEquals(HttpStatusCode.BadRequest, bad.status)

        val taken = postJson("/api/user/invite", json.encodeToString(InviteRequest("invited")), adminToken)
        assertEquals(HttpStatusCode.Conflict, taken.status)
    }
}

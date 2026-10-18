package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.PasswordHasher
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.BackupRequest
import org.mr0btain.ticketman.server.request.PasswordResetRequest
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.BackupTicket
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Covers user management and the admin screen routes
 */
@OptIn(ExperimentalUuidApi::class)
class AdminTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val employee = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(developmentMode: Boolean = true): UserDatabase {
        configureTestServer(dataDir, developmentMode)
        startApplication()

        return UserDatabase().apply {
            add(admin)
            add(employee)
        }
    }

    private suspend fun ApplicationTestBuilder.login(username: String, password: String): HttpResponse =
        client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(username, password)))
        }

    private suspend fun ApplicationTestBuilder.token(user: User, password: String = user.password): String =
        json.decodeFromString<AuthResponse>(login(user.username, password).bodyAsText()).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.get(path: String, token: String) =
        client.get(path) { bearerAuth(token) }

    private suspend fun ApplicationTestBuilder.delete(path: String, token: String) =
        client.delete(path) { bearerAuth(token) }

    // ------------------------------------------ Passwords and access ------------------------------------------

    @Test
    fun passwordsAreStoredHashedAndNeverSentBack() = testApplication {
        val users = setup()

        val stored = users.getByUsername(admin.username)!!.password
        assertTrue(PasswordHasher.isHashed(stored), "Password wasn't hashed: $stored")
        assertNotEquals(admin.password, stored)

        val adminToken = token(admin)
        val fetched = post("/api/user/fetch", json.encodeToString(PagedRequest(0, 50)), adminToken).bodyAsText()
        assertFalse(fetched.contains(stored), "Fetch leaked a password hash")
        json.decodeFromString<List<User>>(fetched).forEach { assertEquals("", it.password) }

        val got = json.decodeFromString<User>(post("/api/user/get", json.encodeToString(admin.identifier), adminToken).bodyAsText())
        assertEquals("", got.password)
    }

    @Test
    fun plainTextPasswordIsUpgradedOnLogin() = testApplication {
        val users = setup()
        val legacy = User(username = "old", password = "old_pass", accessLevel = AccessLevel.NORMAL)
        users.add(legacy)

        // Write a plain password the way it was stored before hashing
        org.jetbrains.exposed.v1.jdbc.transactions.transaction {
            org.mr0btain.ticketman.components.database.tables.user.UserEntity.findById(legacy.identifier)!!.password = "old_pass"
        }

        assertEquals(HttpStatusCode.Accepted, login("old", "old_pass").status)
        assertTrue(PasswordHasher.isHashed(users.getById(legacy.identifier)!!.password))
        assertEquals(HttpStatusCode.Accepted, login("old", "old_pass").status)
    }

    @Test
    fun checkCredentialsRouteNeedsTheRightPassword() = testApplication {
        setup()

        val wrong = client.post("/api/auth/user") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(admin.username, "nope")))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
    }

    @Test
    fun deactivatedUserCantLogInOrUseOldToken() = testApplication {
        val users = setup()
        val employeeToken = token(employee)

        users.update(employee.copy(password = "", status = UserStatus.DEACTIVATED))

        assertEquals(HttpStatusCode.Unauthorized, login(employee.username, employee.password).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            post("/api/data/customer/fetch", json.encodeToString(PagedRequest(0, 10)), employeeToken).status
        )
    }

    @Test
    fun noneAccessLevelDoesNotPassAdminCheck() = testApplication {
        val users = setup()
        val outsider = User(username = "outsider", password = "outsider_pass", accessLevel = AccessLevel.NORMAL)
        users.add(outsider)
        val outsiderToken = token(outsider)

        // NONE's level number is the highest, it still must not count as admin
        users.update(outsider.copy(password = "", accessLevel = AccessLevel.NONE))

        assertEquals(HttpStatusCode.Unauthorized, get("/api/admin/stats", outsiderToken).status)
    }

    @Test
    fun nonAdminCantUseAdminRoutes() = testApplication {
        setup()
        val employeeToken = token(employee)

        assertEquals(HttpStatusCode.Unauthorized, get("/api/admin/stats", employeeToken).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/api/admin/sessions", employeeToken).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/api/user/add", json.encodeToString(User(username = "x", password = "y")), employeeToken).status)
    }

    @Test
    fun meReturnsTheLoggedInUser() = testApplication {
        setup()

        val response = get("/api/user/me", token(employee))
        assertEquals(HttpStatusCode.OK, response.status)

        val me = json.decodeFromString<User>(response.bodyAsText())
        assertEquals(employee.identifier, me.identifier)
        assertEquals(AccessLevel.NORMAL, me.accessLevel)
        assertEquals("", me.password)
    }

    @Test
    fun searchUsesTheClientsSearchQuery() = testApplication {
        setup()

        val response = post("/api/user/search", json.encodeToString(SearchQuery("tec", 10)), token(admin))
        assertEquals(HttpStatusCode.OK, response.status)

        val results = json.decodeFromString<List<SearchResult>>(response.bodyAsText())
        assertTrue(results.any { it.identifier == employee.identifier }, "Search didn't find the user: $results")
    }

    // ------------------------------------------ User management ------------------------------------------

    @Test
    fun adminCanAddUserWhoCanThenLogIn() = testApplication {
        setup()
        val adminToken = token(admin)
        val newUser = User(username = "Newbie", password = "new_pass", name = "new:person", accessLevel = AccessLevel.RESTRICTED)

        assertEquals(HttpStatusCode.Created, post("/api/user/add", json.encodeToString(newUser), adminToken).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/user/add", json.encodeToString(newUser.copy(identifier = Uuid.generateV7())), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/user/add", json.encodeToString(User(username = "nopass")), adminToken).status)

        // Usernames are stored lowercase, logging in shouldn't care about case
        assertEquals(HttpStatusCode.Accepted, login("Newbie", "new_pass").status)
    }

    @Test
    fun updateKeepsPasswordAndBlocksLosingTheLastAdmin() = testApplication {
        val users = setup()
        val adminToken = token(admin)

        val updated = employee.copy(password = "", email = "tech@shop.test", accessLevel = AccessLevel.ADMIN)
        assertEquals(HttpStatusCode.Accepted, post("/api/user/update", json.encodeToString(updated), adminToken).status)
        assertEquals("tech@shop.test", users.getById(employee.identifier)!!.email)

        // Blank password in an update leaves the old one working
        assertEquals(HttpStatusCode.Accepted, login(employee.username, employee.password).status)

        // Can't demote yourself
        val selfDemote = admin.copy(password = "", accessLevel = AccessLevel.NORMAL)
        assertEquals(HttpStatusCode.Conflict, post("/api/user/update", json.encodeToString(selfDemote), adminToken).status)

        // Demoting the other admin is fine while another admin remains
        val demote = updated.copy(accessLevel = AccessLevel.NORMAL)
        assertEquals(HttpStatusCode.Accepted, post("/api/user/update", json.encodeToString(demote), adminToken).status)
        assertEquals(2, users.countByAccessLevel(AccessLevel.ADMIN), "Only boss and the admin made on start should be admins")
    }

    @Test
    fun passwordResetChangesPasswordAndEndsTheirSession() = testApplication {
        setup()
        val adminToken = token(admin)
        val employeeToken = token(employee)

        val response = post("/api/user/password/${employee.identifier}", json.encodeToString(PasswordResetRequest("fresh_pass")), adminToken)
        assertEquals(HttpStatusCode.Accepted, response.status)

        assertEquals(HttpStatusCode.Unauthorized, login(employee.username, employee.password).status)
        assertEquals(HttpStatusCode.Accepted, login(employee.username, "fresh_pass").status)
        assertEquals(HttpStatusCode.Unauthorized, get("/api/user/me", employeeToken).status)
    }

    @Test
    fun removeUserMatchesClientRouteAndBlocksSelfDelete() = testApplication {
        val users = setup()
        val adminToken = token(admin)

        assertEquals(HttpStatusCode.Conflict, delete("/api/user/remove/${admin.identifier}", adminToken).status)
        assertEquals(HttpStatusCode.Accepted, delete("/api/user/remove/${employee.identifier}", adminToken).status)
        assertNull(users.getById(employee.identifier))
        assertEquals(HttpStatusCode.NotFound, delete("/api/user/remove/${employee.identifier}", adminToken).status)
    }

    // ------------------------------------------ Admin screen ------------------------------------------

    @Test
    fun statsCountEverything() = testApplication {
        setup()
        val adminToken = token(admin)
        post("/api/admin/seed", json.encodeToString(SeedRequest(tickets = 6, customers = 2, inventory = 3, users = 0)), adminToken)

        val response = get("/api/admin/stats?lowStock=1000000&recent=4", adminToken)
        assertEquals(HttpStatusCode.OK, response.status)

        val stats = json.decodeFromString<AdminStatsResponse>(response.bodyAsText())
        assertEquals(6, stats.totalTickets)
        assertEquals(6, stats.ticketsByStatus.values.sum())
        assertEquals(stats.openTickets, stats.ticketsByPriority.values.sum())
        assertEquals(6, stats.ticketsCreatedLastWeek)
        assertEquals(4, stats.recentTickets.size)
        assertEquals(2, stats.totalCustomers)
        assertEquals(3, stats.totalInventory)
        // boss, tech and the admin made on start
        assertEquals(3, stats.totalUsers)
        assertEquals(1, stats.activeSessions)
    }

    @Test
    fun lowStockOnlyReturnsItemsAtOrBelowThreshold() = testApplication {
        setup()
        val adminToken = token(admin)

        listOf(Inventory(name = "screen", stock = 1), Inventory(name = "battery", stock = 3), Inventory(name = "case", stock = 40))
            .forEach { post("/api/data/inventory/add", json.encodeToString(it), adminToken) }

        val items = json.decodeFromString<List<Inventory>>(get("/api/admin/inventory/low-stock?threshold=3", adminToken).bodyAsText())
        assertEquals(listOf("screen", "battery"), items.map { it.name })
    }

    @Test
    fun sessionsListAndRevoke() = testApplication {
        setup()
        val adminToken = token(admin)
        val employeeToken = token(employee)

        val sessions = json.decodeFromString<List<SessionResponse>>(get("/api/admin/sessions", adminToken).bodyAsText())
        assertEquals(setOf(admin.identifier, employee.identifier), sessions.map { it.userIdentifier }.toSet())
        assertTrue(sessions.all { it.createdAt != null && it.expiresAt != null })

        val employeeSession = sessions.single { it.userIdentifier == employee.identifier }.sessionId
        assertEquals(HttpStatusCode.Accepted, delete("/api/admin/sessions/$employeeSession", adminToken).status)

        // Revoked access token stops working straight away
        assertEquals(HttpStatusCode.Unauthorized, get("/api/user/me", employeeToken).status)
        assertEquals(HttpStatusCode.NotFound, delete("/api/admin/sessions/$employeeSession", adminToken).status)
    }

    @Test
    fun serverInfo() = testApplication {
        setup()
        val adminToken = token(admin)

        val info = json.decodeFromString<ServerInfoResponse>(get("/api/admin/server", adminToken).bodyAsText())
        assertTrue(info.databaseSizeBytes > 0)
        assertTrue(info.developmentMode)
    }

    @Test
    fun backupIsBuiltThenDownloadedWithItsLink() = testApplication {
        setup()
        val adminToken = token(admin)

        val response = post("/api/admin/backup", json.encodeToString(BackupRequest(includeImages = false)), adminToken)
        assertEquals(HttpStatusCode.Created, response.status)
        val ticket = json.decodeFromString<BackupTicket>(response.bodyAsText())
        assertFalse(ticket.includesImages)
        assertTrue(ticket.fileName.endsWith("-database.zip"))

        // No login needed, the token is the key
        val download = client.get(ticket.downloadPath)
        assertEquals(HttpStatusCode.OK, download.status)
        assertTrue(download.headers[HttpHeaders.ContentDisposition].orEmpty().contains(ticket.fileName))

        val bytes = download.readRawBytes()
        assertEquals(ticket.sizeBytes, bytes.size.toLong())
        assertEquals(ticket.sha256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

        val entries = zipEntries(bytes)
        assertEquals(setOf(BackupManifest.FILE_NAME, BackupManifest.DATABASE_FILE), entries.keys)
        val manifest = json.decodeFromString<BackupManifest>(entries.getValue(BackupManifest.FILE_NAME).decodeToString())
        assertFalse(manifest.includesImages)
        // The database copy is H2's own zip
        assertTrue(entries.getValue(BackupManifest.DATABASE_FILE).take(2) == listOf('P'.code.toByte(), 'K'.code.toByte()))

        // A dropped download can pick up where it left off
        val rest = client.get(ticket.downloadPath) { header(HttpHeaders.Range, "bytes=10-") }
        assertEquals(HttpStatusCode.PartialContent, rest.status)
        assertEquals(bytes.size - 10, rest.readRawBytes().size)
    }

    @Test
    fun backupLinksNeedTheRightTokenAndAnAdmin() = testApplication {
        setup()

        val employeeAttempt = post("/api/admin/backup", json.encodeToString(BackupRequest()), token(employee))
        assertEquals(HttpStatusCode.Forbidden.value / 100, employeeAttempt.status.value / 100)

        val ticket = json.decodeFromString<BackupTicket>(
            post("/api/admin/backup", json.encodeToString(BackupRequest()), token(admin)).bodyAsText()
        )
        val withoutToken = ticket.downloadPath.substringBefore('?')

        assertEquals(HttpStatusCode.NotFound, client.get(withoutToken).status)
        assertEquals(HttpStatusCode.NotFound, client.get("$withoutToken?token=guess").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/backup/download/nope?token=guess").status)
    }

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
            }
        }
    }

    @Test
    fun seedingIsBlockedOutsideDevelopmentMode() = testApplication {
        setup(developmentMode = false)

        val response = post("/api/admin/seed", json.encodeToString(SeedRequest()), token(admin))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun shopProfileCanBeReadByAnyoneAndSetByAdmin() = testApplication {
        setup()
        val adminToken = token(admin)
        val employeeToken = token(employee)
        val profile = ShopProfile(name = "Fix It Shop", phone = "555-0100", invoiceFooter = "Thanks!")

        assertEquals(ShopProfile(), json.decodeFromString<ShopProfile>(get("/api/shop", employeeToken).bodyAsText()))
        assertEquals(HttpStatusCode.Unauthorized, post("/api/shop/update", json.encodeToString(profile), employeeToken).status)
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(profile), adminToken).status)
        assertEquals(profile, json.decodeFromString<ShopProfile>(get("/api/shop", employeeToken).bodyAsText()))

        // Second save updates the same row
        val changed = profile.copy(name = "Fix It Faster")
        post("/api/shop/update", json.encodeToString(changed), adminToken)
        assertEquals(changed, json.decodeFromString<ShopProfile>(get("/api/shop", adminToken).bodyAsText()))
    }

    @Test
    fun auditLogRecordsActionsNewestFirst() = testApplication {
        setup()
        val adminToken = token(admin)
        login(employee.username, "wrong")
        post("/api/data/inventory/add", json.encodeToString(Inventory(name = "logged part")), adminToken)

        val response = post("/api/admin/audit/fetch", json.encodeToString(PagedRequest(0, 50)), adminToken)
        assertEquals(HttpStatusCode.OK, response.status)

        val entries = json.decodeFromString<List<AuditEntry>>(response.bodyAsText())
        val newest = entries.first()
        assertEquals(AuditAction.CREATE, newest.action)
        assertEquals("inventory", newest.entityType)
        assertEquals(admin.username, newest.actorName)

        assertTrue(entries.any { it.action == AuditAction.LOGIN_FAILED && it.actorName == employee.username })
        assertTrue(entries.any { it.action == AuditAction.LOGIN && it.actorIdentifier == admin.identifier })
    }
}

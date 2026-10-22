package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.transfer.downloadTo
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.server.response.BackupJob
import org.mr0btain.ticketman.server.response.BackupJobState
import org.mr0btain.ticketman.server.response.BackupTicket
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.LoginLimitsResponse
import org.mr0btain.ticketman.server.response.LoginLock
import org.mr0btain.ticketman.server.response.LoginLockKind
import org.mr0btain.ticketman.server.request.LoginUnlockRequest
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Checks the admin and user clients hit the routes the server has, and pass failures through usefully
 */
class AdminRemoteRepositoryTest {
    private val requests = mutableListOf<String>()

    private val backupBytes = ByteArray(300_000) { (it % 251).toByte() }
    private val backupTicket = BackupTicket(
        id = "b1",
        fileName = "ticketman-backup-1.zip",
        sizeBytes = backupBytes.size.toLong(),
        sha256 = "",
        downloadPath = "/api/admin/backup/download/b1?token=t",
        expiresAt = 0,
        includesImages = false
    )

    private val pendingRestore = PendingRestore(
        manifest = BackupManifest(serverVersion = "1.0.0", createdAt = 5, includesImages = true, imageCount = 2, imageBytes = 10),
        sizeBytes = 4,
        stagedAt = 6
    )
    private var hasPendingRestore = false
    private var canRestart = true

    // False for a server from before backups were built in the background
    private var backgroundBackups = true
    private var backupStatusAsks = 0
    private var backupFails = false
    private val runningBackup = BackupJob(id = "j1", state = BackupJobState.RUNNING, startedAt = 1)
    private val restoreBodies = mutableListOf<Pair<String, ByteArray>>()
    private val unlockBodies = mutableListOf<String>()

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}${request.url.encodedQuery.let { if (it.isBlank()) "" else "?$it" }}"

        when (request.url.encodedPath) {
            "/api/admin/stats" -> respond(Json.encodeToString(AdminStatsResponse(openTickets = 3, totalTickets = 5)), HttpStatusCode.OK, json)
            "/api/admin/backup" -> respond(Json.encodeToString(backupTicket), HttpStatusCode.Created, json)
            "/api/admin/backup/start" -> if (backgroundBackups) respond(Json.encodeToString(runningBackup), HttpStatusCode.Accepted, json)
                else respond("", HttpStatusCode.NotFound)
            // Still building the first time it's asked
            "/api/admin/backup/status/j1" -> respond(
                Json.encodeToString(
                    when {
                        backupStatusAsks++ == 0 -> runningBackup
                        backupFails -> runningBackup.copy(state = BackupJobState.FAILED, error = "The backup couldn't be made, check the server log")
                        else -> runningBackup.copy(state = BackupJobState.DONE, ticket = backupTicket)
                    }
                ),
                HttpStatusCode.OK, json
            )
            "/api/admin/backup/download/b1" -> respond(backupBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/zip"))
            "/api/admin/restore" -> when (request.method) {
                HttpMethod.Post -> {
                    restoreBodies += request.body.contentType.toString() to request.body.toByteArray()
                    respond(Json.encodeToString(pendingRestore), HttpStatusCode.Accepted, json)
                }
                HttpMethod.Get -> if (hasPendingRestore) respond(Json.encodeToString(pendingRestore), HttpStatusCode.OK, json)
                    else respond("", HttpStatusCode.NoContent)
                else -> respond("", HttpStatusCode.Accepted)
            }
            "/api/admin/restart" -> if (canRestart) respond("", HttpStatusCode.Accepted)
                else respond("This server can't restart itself", HttpStatusCode.Conflict)
            "/api/admin/logins" -> respond(
                Json.encodeToString(LoginLimitsResponse(locks = listOf(LoginLock(LoginLockKind.USERNAME, "tech", 6, 99)), recentFailures = 6)),
                HttpStatusCode.OK, json
            )
            "/api/admin/logins/unlock" -> {
                unlockBodies += request.body.toByteArray().decodeToString()
                respond("", HttpStatusCode.Accepted)
            }
            "/api/admin/seed" -> respond("Seeding is only allowed in development mode", HttpStatusCode.Forbidden)
            "/api/shop" -> respond(Json.encodeToString(ShopProfile(name = "Shop")), HttpStatusCode.OK, json)
            "/api/user/me" -> respond(Json.encodeToString(User(username = "me")), HttpStatusCode.OK, json)
            "/api/user/add" -> respond("Username is already taken", HttpStatusCode.Conflict)
            else -> respond("", HttpStatusCode.Accepted)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout)
    }

    private val admin = AdminRemoteRepository(client).apply { serverUrl = "http://localhost/" }
    private val users = UserRemoteRepository(client).apply { serverUrl = "http://localhost" }

    @Test
    fun statsUseQueryParameters() = runTest {
        val stats = admin.getStats(lowStockThreshold = 7, recentLimit = 3)

        assertEquals(3, stats?.openTickets)
        assertEquals("GET /api/admin/stats?lowStock=7&recent=3", requests.single())
    }

    @Test
    fun loginLocksAreReadAndLifted() = runTest {
        val limits = assertNotNull(admin.getLoginLimits())
        assertEquals("tech", limits.locks.single().key)

        assertIs<TmResult.Success<*>>(admin.unlockLogin(LoginLockKind.USERNAME, "tech"))
        assertEquals(listOf("GET /api/admin/logins", "POST /api/admin/logins/unlock"), requests)
        assertEquals(LoginUnlockRequest(LoginLockKind.USERNAME, "tech"), Json.decodeFromString(unlockBodies.single()))
    }

    @Test
    fun backupIsPreparedThenStreamedToAFile() = runTest {
        val prepared = admin.prepareBackup(includeImages = false)

        assertIs<TmResult.Success<BackupTicket>>(prepared)
        assertEquals(listOf("POST /api/admin/backup/start", "GET /api/admin/backup/status/j1", "GET /api/admin/backup/status/j1"), requests)
        assertEquals("http://localhost/api/admin/backup/download/b1?token=t", admin.backupDownloadUrl(prepared.data))

        val file = Buffer()
        val progress = mutableListOf<Long>()
        val downloaded = client.downloadTo(admin.backupDownloadUrl(prepared.data), backupBytes.size.toLong(), { file }) { received, _ ->
            progress += received
        }

        assertEquals(TmResult.Success(backupBytes.size.toLong()), downloaded)
        assertContentEquals(backupBytes, file.readByteArray())
        // Written as it arrived, not all at the end
        assertTrue(progress.size > 1, "$progress")
    }

    @Test
    fun aBackupThatFailsOnTheServerSaysWhy() = runTest {
        backupFails = true
        val prepared = admin.prepareBackup(includeImages = true)

        assertIs<TmResult.Error<ServerError>>(prepared)
        assertEquals("The backup couldn't be made, check the server log", prepared.error.message)
    }

    @Test
    fun anOlderServerBuildsTheBackupInOneRequest() = runTest {
        backgroundBackups = false
        val prepared = admin.prepareBackup(includeImages = false)

        assertEquals(TmResult.Success(backupTicket), prepared)
        assertEquals(listOf("POST /api/admin/backup/start", "POST /api/admin/backup"), requests)
    }

    @Test
    fun restoreUploadsTheArchiveAsTheBody() = runTest {
        val result = admin.uploadRestore(ByteArrayContent(byteArrayOf(80, 75, 3, 4), ContentType.Application.Zip))

        assertEquals(TmResult.Success(pendingRestore), result)
        val (type, body) = restoreBodies.single()
        assertEquals(ContentType.Application.Zip.toString(), type)
        assertContentEquals(byteArrayOf(80, 75, 3, 4), body)
    }

    @Test
    fun noPendingRestoreIsNull() = runTest {
        assertNull(admin.getPendingRestore())

        hasPendingRestore = true
        assertEquals(pendingRestore, admin.getPendingRestore())
    }

    @Test
    fun restartPostsAndSaysWhyWhenItCant() = runTest {
        assertIs<TmResult.Success<*>>(admin.restartServer())
        assertEquals(listOf("POST /api/admin/restart"), requests)

        canRestart = false
        val refused = admin.restartServer()
        assertIs<TmResult.Error<*>>(refused)
        assertEquals("This server can't restart itself", (refused as TmResult.Error).error.message)
    }

    @Test
    fun shortDownloadsFail() = runTest {
        val downloaded = client.downloadTo("http://localhost/api/admin/backup/download/b1", backupBytes.size + 1L, { Buffer() })

        assertIs<TmResult.Error<*>>(downloaded)
    }

    @Test
    fun failuresCarryTheServersReason() = runTest {
        val result = admin.seed(SeedRequest())

        assertIs<TmResult.Error<*>>(result)
        val error = (result as TmResult.Error).error
        assertEquals(NetworkError.FORBIDDEN, error.type)
        assertEquals("Seeding is only allowed in development mode", error.message)
    }

    @Test
    fun shopAndSessionRoutes() = runTest {
        assertEquals("Shop", admin.getShopProfile()?.name)
        admin.updateShopProfile(ShopProfile())
        admin.revokeSession("session-1")
        admin.revokeUserSessions(Uuid.parse("00000000-0000-7000-8000-000000000001"))

        assertEquals(
            listOf(
                "GET /api/shop",
                "POST /api/shop/update",
                "DELETE /api/admin/sessions/session-1",
                "DELETE /api/admin/sessions/user/00000000-0000-7000-8000-000000000001"
            ),
            requests
        )
    }

    @Test
    fun userRoutesMatchTheServer() = runTest {
        val user = User(username = "new")

        assertEquals("me", users.getMe()?.username)
        users.update(user)
        users.remove(user.identifier)
        users.resetPassword(user.identifier, "pass")

        assertEquals(
            listOf(
                "GET /api/user/me",
                "POST /api/user/update",
                "DELETE /api/user/remove/${user.identifier}",
                "POST /api/user/password/${user.identifier}"
            ),
            requests
        )
    }

    @Test
    fun addingATakenUsernameReportsTheConflict() = runTest {
        val result = users.add(User(username = "taken"))

        assertIs<TmResult.Error<*>>(result)
        assertEquals("Username is already taken", (result as TmResult.Error).error.message)
    }

    @Test
    fun noServerSkipsRequests() = runTest {
        val unset = AdminRemoteRepository(client)

        assertNull(unset.getStats(5, 10))
        assertIs<TmResult.Error<*>>(unset.revokeSession("session-1"))
        assertTrue(requests.isEmpty())
    }
}

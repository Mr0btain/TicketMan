package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.ServerLogs
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.applogger.AppLogger
import org.mr0btain.applogger.LogOverview
import org.mr0btain.applogger.LogSettings
import org.mr0btain.applogger.LogTail
import org.mr0btain.ticketman.server.AdminPaths
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.io.File
import kotlin.test.*

/**
 * The server's log: saved to files from start up, followed live, downloaded by range, and kept as an admin sets
 */
class LogsTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val employee = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(employee)
        }
    }

    private suspend fun ApplicationTestBuilder.token(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private val logsFolder get() = ServerLogs.folderFor(dataDir.toString())

    @Test
    fun startUpIsSavedAndAdminsReadIt() = testApplication {
        setup()
        val token = token(admin)
        AppLogger.e("LogsTest", "Something to find", IllegalStateException("boom"))

        val overview = json.decodeFromString<LogOverview>(
            client.get("${ApiRoutes.ADMIN}${AdminPaths.LOGS}") { bearerAuth(token) }.bodyAsText()
        )
        assertTrue(overview.storing)
        assertEquals(1, overview.files.size)
        assertTrue(overview.files.single().current)
        val file = File(logsFolder, overview.files.single().name)
        assertTrue(file.readText().contains("Server initialized!"), "Start up, logged before the routes, is in the file")

        // Live, from memory
        val tail = json.decodeFromString<LogTail>(
            client.get("${ApiRoutes.ADMIN}${AdminPaths.LOG_TAIL}?after=0&limit=1000") { bearerAuth(token) }.bodyAsText()
        )
        val found = tail.lines.last { it.tag == "LogsTest" }
        assertTrue(found.message.contains("IllegalStateException: boom"))
        val newer = json.decodeFromString<LogTail>(
            client.get("${ApiRoutes.ADMIN}${AdminPaths.LOG_TAIL}?after=${found.sequence}") { bearerAuth(token) }.bodyAsText()
        )
        assertTrue(newer.lines.all { it.sequence > found.sequence })
        assertEquals(tail.run, newer.run)

        // Downloaded by range: everything holds it, a range before it doesn't
        val everything = client.get("${ApiRoutes.ADMIN}${AdminPaths.LOG_DOWNLOAD}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, everything.status)
        assertTrue(everything.bodyAsText().contains("Something to find"))
        assertTrue(everything.headers[HttpHeaders.ContentDisposition].orEmpty().contains("ticketman-server.log"))

        val before = client.get("${ApiRoutes.ADMIN}${AdminPaths.LOG_DOWNLOAD}?from=0&to=1000") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, before.status)
        assertEquals("", before.bodyAsText())

        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("${ApiRoutes.ADMIN}${AdminPaths.LOG_DOWNLOAD}?from=10&to=5") { bearerAuth(token) }.status
        )
    }

    @Test
    fun settingsAreSavedAndOnlyForAdmins() = testApplication {
        setup()
        val adminToken = token(admin)
        val techToken = token(employee)

        listOf(AdminPaths.LOGS, AdminPaths.LOG_TAIL, AdminPaths.LOG_DOWNLOAD).forEach { path ->
            assertEquals(HttpStatusCode.Forbidden, client.get("${ApiRoutes.ADMIN}$path") { bearerAuth(techToken) }.status, path)
        }

        // Out of range values are clamped rather than refused
        val saved = client.post("${ApiRoutes.ADMIN}${AdminPaths.LOG_SETTINGS}") {
            bearerAuth(adminToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LogSettings(keepFiles = 0, maxTotalMb = 50, rolloverMinute = 390)))
        }
        assertEquals(HttpStatusCode.OK, saved.status)
        assertEquals(LogSettings(keepFiles = 1, maxTotalMb = 50, rolloverMinute = 390), json.decodeFromString<LogOverview>(saved.bodyAsText()).settings)

        val file = File(logsFolder, ServerLogs.SETTINGS_FILE)
        assertEquals(LogSettings(1, 50, 390), json.decodeFromString<LogSettings>(file.readText()))
    }

    @Test
    fun savedSettingsAreUsedOnTheNextStart() {
        File(logsFolder, ServerLogs.SETTINGS_FILE).apply {
            parentFile.mkdirs()
            writeText(json.encodeToString(LogSettings(keepFiles = 7, maxTotalMb = 3, rolloverMinute = 60)))
        }

        testApplication {
            setup()
            val overview = json.decodeFromString<LogOverview>(
                client.get("${ApiRoutes.ADMIN}${AdminPaths.LOGS}") { bearerAuth(token(admin)) }.bodyAsText()
            )
            assertEquals(LogSettings(7, 3, 60), overview.settings)
            assertTrue(overview.files.single().name.endsWith("_0100.log"))
        }
    }
}

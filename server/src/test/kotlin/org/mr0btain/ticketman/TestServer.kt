package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import org.mr0btain.ticketman.logging.LogBook
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.server.request.BackupRequest
import org.mr0btain.ticketman.server.response.BackupJob
import org.mr0btain.ticketman.server.response.BackupJobState
import org.mr0btain.ticketman.server.response.BackupTicket

/**
 * Temp directory for a test's database, keeping it out of the real data directory
 */
fun createTestDataDir(): Path = createTempDirectory("ticketman-test")

/**
 * Runs the server module with test config, since testApplication doesn't load application.yaml
 * @param developmentMode Ktor's test default is on, which allows dev only routes like seeding
 * @param extraConfig More config entries, like `"env.trustedProxies" to "none"`
 */
fun ApplicationTestBuilder.configureTestServer(
    dataDir: Path,
    developmentMode: Boolean = true,
    extraConfig: Map<String, String> = emptyMap()
) {
    serverConfig {
        this.developmentMode = developmentMode
    }
    environment {
        config = MapApplicationConfig(
            "env.dataPath" to dataDir.invariantSeparatorsPathString,
            "jwt.audience" to "test-audience",
            "jwt.issuer" to "http://localhost",
            "jwt.realm" to "TicketMan Test",
            "jwt.secret" to "test-secret",
            *extraConfig.toList().toTypedArray()
        )
    }
    application {
        module()
    }
}

/**
 * Shuts down the test database and deletes its directory
 */
@OptIn(ExperimentalPathApi::class)
fun cleanupTestDataDir(dataDir: Path) {
    // H2 is opened with DB_CLOSE_DELAY=-1, so it stays open (and locks its files) until shut down
    try {
        transaction { exec("SHUTDOWN") }
    } catch (_: Exception) {
        // No database was opened
    }

    // The log file is held open while a server is attached, which Windows won't delete
    LogBook.detach()

    dataDir.deleteRecursively()
    assertFalse(dataDir.exists(), "Temp data directory was not removed: $dataDir")
}

/**
 * Builds a backup the way clients do, starting it and asking after it until it's built
 * @return The finished backup's ticket
 */
suspend fun ApplicationTestBuilder.buildBackup(token: String, includeImages: Boolean = false): BackupTicket {
    val json = Json { ignoreUnknownKeys = true }
    val started = client.post("/api/admin/backup/start") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(BackupRequest(includeImages)))
    }
    assertEquals(HttpStatusCode.Accepted, started.status, started.bodyAsText())

    var job = json.decodeFromString<BackupJob>(started.bodyAsText())
    repeat(400) {
        if (job.state != BackupJobState.RUNNING) return@repeat
        Thread.sleep(25)
        job = json.decodeFromString(client.get("/api/admin/backup/status/${job.id}") { bearerAuth(token) }.bodyAsText())
    }
    return assertNotNull(job.ticket, "The backup wasn't built: ${job.error}")
}

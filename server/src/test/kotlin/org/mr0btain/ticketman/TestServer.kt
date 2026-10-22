package org.mr0btain.ticketman

import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.assertFalse

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

    dataDir.deleteRecursively()
    assertFalse(dataDir.exists(), "Temp data directory was not removed: $dataDir")
}

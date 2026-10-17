package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import java.io.File
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Admin things that aren't a straight database query
 * @param dataPath Folder the server was started with, shown on the admin screen
 */
@Suppress("PrivatePropertyName")
class AdminService(
    private val database: LocalDatabase,
    private val serverName: String,
    private val dataPath: String,
    private val developmentMode: Boolean
) {
    private val TAG = "AdminService"
    private val SESSIONS_TAG = "$TAG:Sessions"
    private val BACKUP_TAG = "$TAG:Backup"

    private val startedAt = Clock.System.now().toEpochMilliseconds()

    /** Logged in users, newest login first */
    fun getSessions(): List<SessionResponse> {
        return database.tokenDatabase.getAll().mapNotNull { refreshToken ->
            val identifier = Uuid.parseOrNull(refreshToken.identifier) ?: return@mapNotNull null
            val user = database.userDatabase.getById(identifier) ?: return@mapNotNull null

            // Only reading the dates for display, the token was verified when it was handed out
            val decoded = try {
                JWT.decode(refreshToken.token)
            } catch (ex: Exception) {
                AppLogger.i(SESSIONS_TAG, "Couldn't decode token for $identifier")
                null
            }

            SessionResponse(
                userIdentifier = identifier,
                username = user.username,
                name = user.name,
                accessLevel = user.accessLevel,
                issuedAt = decoded?.issuedAtAsInstant?.toEpochMilli(),
                expiresAt = decoded?.expiresAtAsInstant?.toEpochMilli()
            )
        }.sortedByDescending { it.issuedAt ?: 0 }
    }

    fun getServerInfo(): ServerInfoResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        return ServerInfoResponse(
            name = serverName,
            version = SERVER_VERSION,
            startedAt = startedAt,
            uptimeMillis = now - startedAt,
            dataPath = dataPath,
            databaseSizeBytes = database.sizeOnDisk(),
            javaVersion = System.getProperty("java.version") ?: "",
            operatingSystem = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            developmentMode = developmentMode
        )
    }

    /**
     * Makes a zip backup of the database and hands back its bytes
     */
    fun createBackup(): ByteArray? {
        val file = File.createTempFile("ticketman-backup", ".zip")

        return try {
            // H2 won't write over an existing file
            file.delete()

            if (database.backupTo(file)) file.readBytes() else null
        } catch (ex: Exception) {
            AppLogger.e(BACKUP_TAG, SimpleResult.FAIL, ex)
            null
        } finally {
            file.delete()
        }
    }

    companion object {
        // Keep in step with version in server/build.gradle.kts
        const val SERVER_VERSION = "1.0.0"
    }
}

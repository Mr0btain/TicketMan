package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.ServerCertificateInfo
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
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
    private val developmentMode: Boolean,
    private val certificate: ServerCertificateInfo? = null
) {
    private val TAG = "AdminService"
    private val SESSIONS_TAG = "$TAG:Sessions"

    private val startedAt = Clock.System.now().toEpochMilliseconds()

    /** Logged in devices, most recently used first */
    fun getSessions(): List<SessionResponse> {
        return database.tokenDatabase.getAll().mapNotNull { session ->
            val identifier = Uuid.parseOrNull(session.userIdentifier) ?: return@mapNotNull null
            val user = database.userDatabase.getById(identifier) ?: return@mapNotNull null

            // Only reading the expiry for display, the token was verified when it was handed out
            val decoded = try {
                JWT.decode(session.token)
            } catch (ex: Exception) {
                AppLogger.i(SESSIONS_TAG, "Couldn't decode token for $identifier")
                null
            }

            SessionResponse(
                sessionId = session.sessionId,
                userIdentifier = identifier,
                username = user.username,
                name = user.name,
                accessLevel = user.accessLevel,
                deviceName = session.deviceName,
                kind = session.kind,
                createdAt = session.createdAt.takeIf { it > 0 },
                lastUsedAt = session.lastUsedAt.takeIf { it > 0 },
                expiresAt = decoded?.expiresAtAsInstant?.toEpochMilli()
            )
        }.sortedByDescending { it.lastUsedAt ?: 0 }
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
            imagesSizeBytes = database.imageStore.sizeOnDisk(),
            javaVersion = System.getProperty("java.version") ?: "",
            operatingSystem = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            developmentMode = developmentMode,
            certificate = certificate
        )
    }

    companion object {
        // Keep in step with version in server/build.gradle.kts
        const val SERVER_VERSION = "1.0.0"
    }
}

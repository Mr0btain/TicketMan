package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.tls.ProxySettings
import org.mr0btain.ticketman.components.tls.ServerTls
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.ServerCertificateInfo
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import java.io.File
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Admin things that aren't a straight database query
 * @param dataPath Folder the server was started with, shown on the admin screen
 * @param certificate The certificate HTTPS started with, null when HTTPS is off
 * @param tlsFolder Where a made certificate is kept, null when it's supplied, so it can't be renewed here
 * @param proxy How the server sits behind a reverse proxy, for the Server tab's diagnostics
 * @param restart Restarting from the admin screen, when something will start the server again
 */
@Suppress("PrivatePropertyName")
class AdminService(
    private val database: LocalDatabase,
    private val serverName: String,
    private val dataPath: String,
    private val developmentMode: Boolean,
    private val certificate: ServerCertificateInfo? = null,
    private val tlsFolder: File? = null,
    private val backupService: BackupService? = null,
    private val restoreService: RestoreService? = null,
    private val proxy: ProxySettings = ProxySettings(enabled = false),
    val restart: ServerRestart = ServerRestart(enabled = false)
) {
    private val TAG = "AdminService"
    private val SESSIONS_TAG = "$TAG:Sessions"
    private val RENEW_TAG = "$TAG:RenewCertificate"

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

    /**
     * What the admin screen's Server tab shows: name, version, uptime, data sizes, Java and OS, mode, certificate, and
     * how the request reached the server, so an admin can see a reverse proxy is set up right
     * @param clientAddress The asking device's address as the server sees it (`request.origin.remoteHost`)
     * @param forwarded Whether that came from a trusted proxy's forwarded headers
     */
    fun getServerInfo(clientAddress: String = "", forwarded: Boolean = false): ServerInfoResponse {
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
            certificate = certificateInfo(),
            scheduledBackup = backupService?.scheduleInfo(),
            lastRestore = restoreService?.lastRestore(),
            proxy = proxy.enabled,
            trustedProxies = if (proxy.trustAny) listOf("any address (TRUST_PROXY)") else if (proxy.enabled) proxy.trusted.map { it.text } else emptyList(),
            clientAddress = clientAddress,
            forwarded = forwarded,
            timeZone = ZoneId.systemDefault().id,
            canRestart = restart.enabled
        )
    }

    /** The certificate in use, with the one waiting for the next start if there is one */
    fun certificateInfo(): ServerCertificateInfo? {
        val pending = tlsFolder?.let { folder ->
            try {
                ServerTls.pending(folder)
            } catch (ex: Exception) {
                AppLogger.e(RENEW_TAG, "Couldn't read the waiting certificate", ex)
                null
            }
        }

        return certificate?.copy(
            pendingFingerprint = pending?.fingerprint,
            pendingExpiresAt = pending?.certificate?.notAfter?.time
        )
    }

    /** Whether the certificate is one the server made, so it can make a new one */
    val canRenewCertificate: Boolean get() = certificate?.generated == true && tlsFolder != null

    /**
     * Makes a new certificate to use from the next start
     * @return The certificate info with the new one waiting, null if it couldn't be made
     */
    fun renewCertificate(): ServerCertificateInfo? {
        val folder = tlsFolder?.takeIf { canRenewCertificate } ?: return null

        return try {
            ServerTls.stageNew(folder)
            certificateInfo()
        } catch (ex: Exception) {
            AppLogger.e(RENEW_TAG, "Couldn't make a new certificate", ex)
            null
        }
    }

    /** Drops the certificate waiting for the next start, false if there wasn't one */
    fun cancelCertificateRenewal(): Boolean = tlsFolder?.let { ServerTls.cancelPending(it) } ?: false

    companion object {
        // Keep in step with version in server/build.gradle.kts
        const val SERVER_VERSION = "1.0.0"
    }
}

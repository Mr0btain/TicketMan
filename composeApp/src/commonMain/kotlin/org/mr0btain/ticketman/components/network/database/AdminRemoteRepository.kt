package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.BackupRequest
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.request.StationRegistrationRequest
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.BackupTicket
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.DailyReport
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import org.mr0btain.ticketman.server.response.StationRegistrationResponse
import kotlin.uuid.Uuid

/**
 * Client for the admin screen's routes under /api/admin, plus the shop profile under /api/shop
 */
@Suppress("PrivatePropertyName")
class AdminRemoteRepository(
    private val client: HttpClient
) {
    private val TAG = "AdminRemoteRepository"
    private val STATS_TAG = "$TAG:Stats"
    private val LOW_STOCK_TAG = "$TAG:LowStock"
    private val SESSIONS_TAG = "$TAG:Sessions"
    private val REVOKE_SESSION_TAG = "$TAG:RevokeSession"
    private val SERVER_INFO_TAG = "$TAG:ServerInfo"
    private val BACKUP_TAG = "$TAG:Backup"
    private val RESTORE_TAG = "$TAG:Restore"
    private val SEED_TAG = "$TAG:Seed"
    private val AUDIT_TAG = "$TAG:Audit"
    private val GET_SHOP_TAG = "$TAG:GetShop"
    private val UPDATE_SHOP_TAG = "$TAG:UpdateShop"
    private val STATIONS_TAG = "$TAG:Stations"
    private val DAILY_REPORT_TAG = "$TAG:DailyReport"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = value.trimEnd('/')
        }

    private val adminUrl get() = "$baseUrl/api/admin"
    private val shopUrl get() = "$baseUrl/api/shop"

    suspend fun getStats(lowStockThreshold: Int, recentLimit: Int): AdminStatsResponse? =
        getJson(STATS_TAG, "$adminUrl/stats?lowStock=$lowStockThreshold&recent=$recentLimit")

    /** End of day numbers, for today when [date] (yyyy-mm-dd) is null */
    suspend fun getDailyReport(date: String?): DailyReport? =
        getJson(DAILY_REPORT_TAG, "$adminUrl/report/daily" + (date?.let { "?date=$it" } ?: ""))

    suspend fun getLowStock(threshold: Int, limit: Int): List<Inventory> =
        getJson(LOW_STOCK_TAG, "$adminUrl/inventory/low-stock?threshold=$threshold&limit=$limit") ?: emptyList()

    suspend fun getSessions(): List<SessionResponse> =
        getJson(SESSIONS_TAG, "$adminUrl/sessions") ?: emptyList()

    /** Logs out one device */
    suspend fun revokeSession(sessionId: String): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(REVOKE_SESSION_TAG) {
            client.delete { url("$adminUrl/sessions/$sessionId") }
        }

        return handleActionHttpResponse(REVOKE_SESSION_TAG, response)
    }

    /** Logs a user out of every device */
    suspend fun revokeUserSessions(userIdentifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(REVOKE_SESSION_TAG) {
            client.delete { url("$adminUrl/sessions/user/$userIdentifier") }
        }

        return handleActionHttpResponse(REVOKE_SESSION_TAG, response)
    }

    suspend fun getStations(): List<Station> =
        getJson(STATIONS_TAG, "$adminUrl/stations") ?: emptyList()

    suspend fun approveStation(identifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(STATIONS_TAG) {
            client.post { url("$adminUrl/stations/$identifier/approve") }
        }

        return handleActionHttpResponse(STATIONS_TAG, response)
    }

    suspend fun setKiosk(identifier: Uuid, settings: KioskSettings): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(STATIONS_TAG) {
            client.post {
                url("$adminUrl/stations/$identifier/kiosk")
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
        }

        return handleActionHttpResponse(STATIONS_TAG, response)
    }

    /** Removes a station, signing out whoever is PINned in there */
    suspend fun removeStation(identifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(STATIONS_TAG) {
            client.delete { url("$adminUrl/stations/$identifier") }
        }

        return handleActionHttpResponse(STATIONS_TAG, response)
    }

    /**
     * Registers this device as a station. Needs NORMAL access, then an admin approves it
     * @return The registration, including the secret which is only handed out here
     */
    suspend fun registerStation(deviceId: String, name: String): Result<StationRegistrationResponse, ServerError> {
        if (baseUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkErrorTryCatch(STATIONS_TAG) {
            client.post {
                url("$baseUrl/api/station/register")
                contentType(ContentType.Application.Json)
                setBody(StationRegistrationRequest(deviceId, name))
            }
        }

        return handleDataHttpResponse(STATIONS_TAG, response)
    }

    suspend fun getServerInfo(): ServerInfoResponse? =
        getJson(SERVER_INFO_TAG, "$adminUrl/server")

    /**
     * Has the server build a backup archive. Can take minutes with lots of photos, so there's no overall timeout
     * @param includeImages Photos too, off for a quick database only copy
     */
    suspend fun prepareBackup(includeImages: Boolean): Result<BackupTicket, ServerError> {
        if (baseUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkErrorTryCatch(BACKUP_TAG) {
            client.post {
                url("$adminUrl/backup")
                contentType(ContentType.Application.Json)
                setBody(BackupRequest(includeImages))
                transferTimeout()
            }
        }

        return handleDataHttpResponse(BACKUP_TAG, response)
    }

    /**
     * Sends a backup archive to restore from. The server checks it and keeps it until it next starts
     * @param body The archive, see fileBody
     */
    suspend fun uploadRestore(
        body: OutgoingContent,
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> }
    ): Result<PendingRestore, ServerError> {
        if (baseUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkErrorTryCatch(RESTORE_TAG) {
            client.post {
                url("$adminUrl/restore")
                setBody(body)
                transferTimeout()
                onUpload { sent, total -> onProgress(sent, total) }
            }
        }

        return handleDataHttpResponse(RESTORE_TAG, response)
    }

    /** The restore waiting for the server to restart, null if there isn't one */
    suspend fun getPendingRestore(): PendingRestore? {
        if (baseUrl.isBlank()) return null

        val response = networkErrorTryCatch(RESTORE_TAG) {
            client.get { url("$adminUrl/restore") }
        } ?: return null

        if (response.status == HttpStatusCode.NoContent) return null

        handleHttpResponse<PendingRestore>(RESTORE_TAG, response).onSuccess { return it }
        return null
    }

    suspend fun cancelRestore(): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(RESTORE_TAG) {
            client.delete { url("$adminUrl/restore") }
        }

        return handleActionHttpResponse(RESTORE_TAG, response)
    }

    /** Full url a prepared backup downloads from */
    fun backupDownloadUrl(ticket: BackupTicket): String = "$baseUrl${ticket.downloadPath}"

    suspend fun seed(request: SeedRequest): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(SEED_TAG) {
            client.post {
                url("$adminUrl/seed")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        return handleActionHttpResponse(SEED_TAG, response)
    }

    /** Audit entries newest first */
    suspend fun fetchAudit(offset: Long, limit: Int): List<AuditEntry> {
        if (baseUrl.isNotBlank()) {
            val response = networkErrorTryCatch(AUDIT_TAG) {
                client.post {
                    url("$adminUrl/audit/fetch")
                    contentType(ContentType.Application.Json)
                    setBody(PagedRequest(offset, limit))
                }
            }

            if (response != null) {
                handleHttpResponse<List<AuditEntry>>(AUDIT_TAG, response).onSuccess { return it }
            }
        }

        return emptyList()
    }

    suspend fun getShopProfile(): ShopProfile? =
        getJson(GET_SHOP_TAG, shopUrl)

    suspend fun updateShopProfile(profile: ShopProfile): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(UPDATE_SHOP_TAG) {
            client.post {
                url("$shopUrl/update")
                contentType(ContentType.Application.Json)
                setBody(profile)
            }
        }

        return handleActionHttpResponse(UPDATE_SHOP_TAG, response)
    }

    private suspend inline fun <reified T> getJson(tag: String, requestUrl: String): T? {
        if (baseUrl.isBlank()) return null

        val response = networkErrorTryCatch(tag) {
            client.get { url(requestUrl) }
        } ?: return null

        handleHttpResponse<T>(tag, response).onSuccess { return it }

        return null
    }

    private val noServerError: EmptyResult<ServerError>
        get() = Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
}

package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.SessionResponse
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
    private val SEED_TAG = "$TAG:Seed"
    private val AUDIT_TAG = "$TAG:Audit"
    private val GET_SHOP_TAG = "$TAG:GetShop"
    private val UPDATE_SHOP_TAG = "$TAG:UpdateShop"

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

    suspend fun getServerInfo(): ServerInfoResponse? =
        getJson(SERVER_INFO_TAG, "$adminUrl/server")

    /** Zip of the server's database */
    suspend fun downloadBackup(): Result<ByteArray, ServerError> {
        if (baseUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkErrorTryCatch(BACKUP_TAG) {
            client.get { url("$adminUrl/backup") }
        }

        return when {
            response == null -> Result.Error(ServerError(NetworkError.NO_INTERNET))
            response.status.isSuccess() -> Result.Success(response.readRawBytes())
            else -> {
                val failed = handleActionHttpResponse(BACKUP_TAG, response) as? Result.Error
                Result.Error(failed?.error ?: ServerError(NetworkError.UNKNOWN))
            }
        }
    }

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

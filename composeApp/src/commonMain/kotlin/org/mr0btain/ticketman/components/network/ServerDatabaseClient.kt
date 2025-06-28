package org.mr0btain.ticketman.components.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.network.*
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.components.network.ServerDatabaseClient.Companion.TAG
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.*
import java.util.UUID
import kotlin.collections.map

class ServerDatabaseClient(
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>
) {
    private val USER_NAME = stringPreferencesKey("username")
    private val PASSWORD = stringPreferencesKey("password")
    private val ACCESS_TOKEN = stringPreferencesKey("accessToken")
    private val REFRESH_TOKEN = stringPreferencesKey("refreshToken")

    companion object {
        const val TAG = "ServerDatabaseClient"
    }

    private var serverUrl = ""

    // For updating the server url
    fun changeServerUrl(url: String) {
        serverUrl = url
    }

    @OptIn(InternalAPI::class)
    suspend fun login(): Result<AuthResponse, NetworkError> {
        // Get username and password
        val username = dataStore.data.map { preferences -> preferences[USER_NAME] ?: "" }.first()
        val password = dataStore.data.map { preferences -> preferences[PASSWORD] ?: "" }.first()

        val response = try {
            // Send request and receive response
            httpClient.post {
                url("$serverUrl/api/auth")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<AuthResponse>(response)
    }

    suspend fun refreshTokens(token: String): Result<AuthResponse, NetworkError> {
        val response = try {
            // Send request and receive response
            httpClient.post {
                url("$serverUrl/api/auth/refresh")
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(token))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        // Check if we get a token back or not, else we need to re login
        handleHttpResponse<RefreshTokenResponse>(response)
            .onSuccess { return Result.Success(AuthResponse(UUID.randomUUID(), it.token, token)) }

        return Result.Error(NetworkError.UNKNOWN)
    }

    suspend fun checkServerConnection(): Boolean {
        val response = try {
            httpClient.get("${serverUrl}/")
        } catch (ex: UnresolvedAddressException) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        } catch (ex: SerializationException) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        } catch (ex: Exception) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        }

        return response.status == HttpStatusCode.OK
    }

    suspend fun syncCustomers(customerData: List<CustomerData>): Result<CustomerSyncResponse, NetworkError> {
        val list = customerData.map { item -> item.info.identifier }

        val response = try {
            // Send request and receive response
            httpClient.post {
                url("$serverUrl/api/data/customer/sync")
                contentType(ContentType.Application.Json)
                setBody(SyncRequest(list))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<CustomerSyncResponse>(response)
    }

    suspend fun sendCustomers(customers: List<CustomerData>): Boolean {
        val response = try {
            httpClient.post(urlString = "$serverUrl/api/data/customer/addAll") { customers }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendCustomers", ex.message.toString(), ex)
            return false
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendCustomers", ex.message.toString(), ex)
            return false
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendCustomers", ex.message.toString(), ex)
            return false
        }

        handleHttpResponse<CustomerResponse>(response)
            .onSuccess { return true }
            .onError {
                AppLogger.i("$TAG:SendCustomers", "$it")
                return false
            }

        return false
    }

    suspend fun getCustomers(): Result<List<CustomerData>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "${serverUrl}/api/data/customers/all")
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<List<CustomerData>>(response)
    }

    suspend fun syncTickets(tickets: List<TicketInfo>): Result<TicketSyncResponse, NetworkError> {
        val list = tickets.map { item -> item.identifier }

        val response = try {
            // Send request and receive response
            httpClient.post {
                url("$serverUrl/api/data/ticket/sync")
                contentType(ContentType.Application.Json)
                setBody(SyncRequest(list))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<TicketSyncResponse>(response)
    }

    suspend fun sendTickets(tickets: List<TicketInfo>): Boolean {
        val response = try {
            httpClient.post(urlString = "$serverUrl/api/data/ticket/addAll") { tickets }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendTickets", ex.message.toString(), ex)
            return false
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendTickets", ex.message.toString(), ex)
            return false
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendTickets", ex.message.toString(), ex)
            return false
        }

        handleHttpResponse<CustomerResponse>(response)
            .onSuccess { return true }
            .onError {
                AppLogger.i("$TAG:SendTickets", "$it")
                return false
            }

        return false
    }

    suspend fun getTickets(): Result<List<TicketInfo>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "$serverUrl/api/data/tickets/all")
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<List<TicketInfo>>(response)
    }

    suspend fun getUsers(): Result<List<User>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "$serverUrl/api/data/user/all")
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<List<User>>(response)
    }

    suspend fun syncInventory(inventory: List<InventoryItem>): Result<InventorySyncResponse, NetworkError> {
        val list = inventory.map { item -> item.identifier }

        val response = try {
            // Send request and receive response
            httpClient.post {
                url("$serverUrl/api/data/inventory/sync")
                contentType(ContentType.Application.Json)
                setBody(SyncRequest(list))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<InventorySyncResponse>(response)
    }

    suspend fun sendInventory(inventory: List<InventoryItem>): Boolean {
        val response = try {
            httpClient.post(urlString = "$serverUrl/api/data/inventory/addAll") { inventory }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendInventory", ex.message.toString(), ex)
            return false
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendInventory", ex.message.toString(), ex)
            return false
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.e("$TAG:SendInventory", ex.message.toString(), ex)
            return false
        }

        handleHttpResponse<InventoryResponse>(response)
            .onSuccess { return true }
            .onError {
                AppLogger.i("$TAG:SendInventory", "$it")
                return false
            }

        return false
    }

    suspend fun getInventory(): Result<List<InventoryItem>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "$serverUrl/api/data/inventory/all")
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        return handleHttpResponse<List<InventoryItem>>(response)
    }
}

private suspend inline fun <reified T> handleHttpResponse(response: HttpResponse): Result<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            val response = response.body<T>()
            AppLogger.m("$TAG:CheckServerConnection", "SUCCESS")
            Result.Success(response)
        }

        401 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.UNAUTHORIZED.name)
            Result.Error(NetworkError.UNAUTHORIZED)
        }

        409 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.CONFLICT.name)
            Result.Error(NetworkError.CONFLICT)
        }

        408 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.REQUEST_TIMEOUT.name)
            Result.Error(NetworkError.REQUEST_TIMEOUT)
        }

        413 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.PAYLOAD_TOO_LARGE.name)
            Result.Error(NetworkError.PAYLOAD_TOO_LARGE)
        }

        in 500..599 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.SERVER_ERROR.name)
            Result.Error(NetworkError.SERVER_ERROR)
        }

        else -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.UNKNOWN.name)
            Result.Error(NetworkError.UNKNOWN)
        }
    }
}
package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.network.*
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
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.*

class ServerDatabaseClient (
    private val httpClient: HttpClient
) {
    companion object {
        const val TAG = "ServerDatabaseClient"
    }

    private var serverUrl = ""


    // For updating the server url
    fun changeServerUrl(url: String) { serverUrl = url }

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

        val result: Result<Boolean, NetworkError> = handleHttpResponse(response)

        result
            .onSuccess { return true }
            .onError { return false }

        return false
    }

    suspend fun syncCustomers(customerData: List<CustomerData>): Result<CustomerSyncResponse, NetworkError> {
        val response = try {
            // Send request and receive response
            httpClient.get(
                urlString = "$serverUrl/api/data/customer/sync",
                block = { SyncRequest(customerData.map { item -> item.info.identifier }) }
            )
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
            httpClient.post(urlString = "$serverUrl/api/data/inventory/addAll") { customers }
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
        val response = try {
            // Send request and receive response
            httpClient.get(
                urlString = "$serverUrl/api/data/customer/sync",
                block = { SyncRequest(tickets.map { item -> item.identifier }) }
            )
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
            httpClient.post(urlString = "$serverUrl/api/data/inventory/addAll") { tickets }
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
        val response = try {
            // Send request and receive response
            httpClient.get(
                urlString = "$serverUrl/api/data/inventory/sync",
                block = { SyncRequest(inventory.map { item -> item.identifier }) }
            )
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
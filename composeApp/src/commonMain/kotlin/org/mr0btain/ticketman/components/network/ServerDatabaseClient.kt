package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.*
import kotlin.collections.map

class ServerDatabaseClient(
    private val databaseHttpClient: HttpClient
) {
    companion object {
        const val TAG = "ServerDatabaseClient"
    }

    private var serverUrl = ""

    val customerClient =
        DatabaseClient<CustomerData>("CustomerClient", databaseHttpClient, "$serverUrl/api/data/customer")
    val ticketClient =
        DatabaseClient<TicketInfo>("TicketClient", databaseHttpClient, "$serverUrl/api/data/ticket")
    val inventoryClient =
        DatabaseClient<InventoryItem>("InventoryClient", databaseHttpClient, "$serverUrl/api/data/inventory")

    // For updating the server url
    fun changeServerUrl(url: String) {
        serverUrl = url
    }

    suspend fun syncCustomers(data: List<CustomerData>): List<CustomerData> =
        customerClient.sync(data)

    suspend fun sendCustomers(customers: List<CustomerData>) =
        customerClient.batchSend(customers)

    suspend fun getCustomers(): List<CustomerData> =
        customerClient.getAll()

    // TODO: Maybe add try catch to this when casting? it shouldn't ever not be the type but who knows
    suspend fun getCustomer(identifier: Int): CustomerData? =
        customerClient.get<CustomerData>(identifier) as CustomerData?

    suspend fun syncTickets(tickets: List<TicketInfo>): Result<TicketSyncResponse, NetworkError> {
        val list = tickets.map { item -> item.identifier }

        val response = try {
            // Send request and receive response
            databaseHttpClient.post {
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
            databaseHttpClient.post(urlString = "$serverUrl/api/data/ticket/addAll") { tickets }
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
            databaseHttpClient.get(urlString = "$serverUrl/api/data/tickets/all")
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
            databaseHttpClient.get(urlString = "$serverUrl/api/data/user/all")
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
            databaseHttpClient.post {
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
            databaseHttpClient.post(urlString = "$serverUrl/api/data/inventory/addAll") { inventory }
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
            databaseHttpClient.get(urlString = "$serverUrl/api/data/inventory/all")
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
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
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.data.user.User

/**
 * Main database client for interacting with the data clients
 */
class ServerDatabaseClient(
    private val databaseHttpClient: HttpClient
) {
    companion object {
        const val TAG = "ServerDatabaseClient"
    }

    private var serverUrl = ""

    // Clients used to interact with server. Only real difference is the types and server urls
    // Could possibly combine this into one client somehow. just supplying the url for each call?
    private val customerClient =
        DatabaseClient<CustomerData>("CustomerClient", databaseHttpClient, "$serverUrl/api/data/customer")
    private val ticketClient =
        DatabaseClient<TicketInfo>("TicketClient", databaseHttpClient, "$serverUrl/api/data/ticket")
    private val inventoryClient =
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

    suspend fun removeCustomer(identifier: String) =
        customerClient.remove(identifier)

    suspend fun syncTickets(tickets: List<TicketInfo>) =
        ticketClient.sync(tickets)

    suspend fun sendTickets(tickets: List<TicketInfo>) =
        ticketClient.batchSend(tickets)

    suspend fun getTickets() =
        ticketClient.getAll()

    suspend fun getTicket(identifier: Int) =
        ticketClient.get<TicketInfo>(identifier)

    suspend fun removeTicket(identifier: String) =
        ticketClient.remove(identifier)

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

    suspend fun syncInventory(inventory: List<InventoryItem>) =
        inventoryClient.sync(inventory)

    suspend fun sendInventory(inventory: List<InventoryItem>) =
        inventoryClient.batchSend(inventory)

    suspend fun getInventory() =
        inventoryClient.getAll()

    suspend fun getInventoryItem(identifier: Int) =
        inventoryClient.get<InventoryItem>(identifier)

    suspend fun removeInventoryItem(identifier: String) =
        inventoryClient.remove(identifier)
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
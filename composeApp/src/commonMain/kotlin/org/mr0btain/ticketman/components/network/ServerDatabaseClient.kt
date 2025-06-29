package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.network.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.SimpleError

/**
 * Main database client for interacting with the data clients
 */
class ServerDatabaseClient(
    private val databaseHttpClient: HttpClient
) {
    companion object {
        private const val TAG = "ServerDatabaseClient"
        private const val CUSTOMER_CLIENT_TAG = "$TAG:CustomerClient"
        private const val TICKET_CLIENT_TAG = "$TAG:TicketClient"
        private const val INVENTORY_CLIENT_TAG = "$TAG:InventoryClient"
        private const val CHANGE_SERVER_URL_TAG = "$TAG:ChangeServerUrl"
    }

    private var serverUrl = "http://localhost:8080"

    // Clients used to interact with server. Only real difference is the types and server urls
    // Could possibly combine this into one client somehow. just supplying the url for each call?
    private val customerClient =
        DatabaseClient<CustomerData>(CUSTOMER_CLIENT_TAG, databaseHttpClient, "$serverUrl/api/data/customer")
    private val ticketClient =
        DatabaseClient<TicketInfo>(TICKET_CLIENT_TAG, databaseHttpClient, "$serverUrl/api/data/ticket")
    private val inventoryClient =
        DatabaseClient<InventoryItem>(INVENTORY_CLIENT_TAG, databaseHttpClient, "$serverUrl/api/data/inventory")

    // For updating the server url
    fun changeServerUrl(url: String) {
        println("Server Change: $url")
        AppLogger.m(CHANGE_SERVER_URL_TAG, SimpleError.SUCCESS)
        //serverUrl = url
    }

    suspend fun syncCustomers(data: List<CustomerData>): List<CustomerData> =
        customerClient.sync(data)

    suspend fun sendCustomers(customers: List<CustomerData>) =
        customerClient.batchSend(customers)

    suspend fun getCustomers(): List<CustomerData> =
        customerClient.getAll<CustomerData>()

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
        ticketClient.getAll<TicketInfo>()

    suspend fun getTicket(identifier: Int) =
        ticketClient.get<TicketInfo>(identifier)

    suspend fun removeTicket(identifier: String) =
        ticketClient.remove(identifier)

    // TODO: Gonna be getting rid of this
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

        return handleHttpResponse<List<User>>("$TAG:GetUsers", response)
    }

    suspend fun syncInventory(inventory: List<InventoryItem>) =
        inventoryClient.sync(inventory)

    suspend fun sendInventory(inventory: List<InventoryItem>) =
        inventoryClient.batchSend(inventory)

    suspend fun getInventory() =
        inventoryClient.getAll<InventoryItem>()

    suspend fun getInventoryItem(identifier: Int) =
        inventoryClient.get<InventoryItem>(identifier)

    suspend fun removeInventoryItem(identifier: String) =
        inventoryClient.remove(identifier)
}
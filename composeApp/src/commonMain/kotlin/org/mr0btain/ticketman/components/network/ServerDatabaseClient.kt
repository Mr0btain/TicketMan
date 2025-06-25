package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.util.network.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.User

class ServerDatabaseClient (
    private val httpClient: HttpClient
) {
    private var serverUrl = ""

    // For updating the server url
    fun changeServerUrl(url: String) { serverUrl = url }

    suspend fun getCustomers(): Result<List<CustomerData>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "${serverUrl}/api/data/customers/all")
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        }

        return when (response.status.value) {
            in 200..299 -> {
                val customers = response.body<List<CustomerData>>()
                Result.Success(customers)
            }

            401 -> Result.Error(NetworkError.UNAUTHORIZED)
            409 -> Result.Error(NetworkError.CONFLICT)
            408 -> Result.Error(NetworkError.REQUEST_TIMEOUT)
            413 -> Result.Error(NetworkError.PAYLOAD_TOO_LARGE)

            in 500..599 -> Result.Error(NetworkError.SERVER_ERROR)

            else -> Result.Error(NetworkError.UNKNOWN)
        }
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
        }

        return when (response.status.value) {
            in 200..299 -> {
                val ticketInfos = response.body<List<TicketInfo>>()
                Result.Success(ticketInfos)
            }

            401 -> Result.Error(NetworkError.UNAUTHORIZED)
            409 -> Result.Error(NetworkError.CONFLICT)
            408 -> Result.Error(NetworkError.REQUEST_TIMEOUT)
            413 -> Result.Error(NetworkError.PAYLOAD_TOO_LARGE)

            in 500..599 -> Result.Error(NetworkError.SERVER_ERROR)

            else -> Result.Error(NetworkError.UNKNOWN)
        }
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
        }

        return when (response.status.value) {
            in 200..299 -> {
                val users = response.body<List<User>>()
                Result.Success(users)
            }

            401 -> Result.Error(NetworkError.UNAUTHORIZED)
            409 -> Result.Error(NetworkError.CONFLICT)
            408 -> Result.Error(NetworkError.REQUEST_TIMEOUT)
            413 -> Result.Error(NetworkError.PAYLOAD_TOO_LARGE)

            in 500..599 -> Result.Error(NetworkError.SERVER_ERROR)

            else -> Result.Error(NetworkError.UNKNOWN)
        }
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
        }

        return when (response.status.value) {
            in 200..299 -> {
                val inventoryItems = response.body<List<InventoryItem>>()
                Result.Success(inventoryItems)
            }

            401 -> Result.Error(NetworkError.UNAUTHORIZED)
            409 -> Result.Error(NetworkError.CONFLICT)
            408 -> Result.Error(NetworkError.REQUEST_TIMEOUT)
            413 -> Result.Error(NetworkError.PAYLOAD_TOO_LARGE)

            in 500..599 -> Result.Error(NetworkError.SERVER_ERROR)

            else -> Result.Error(NetworkError.UNKNOWN)
        }
    }
}
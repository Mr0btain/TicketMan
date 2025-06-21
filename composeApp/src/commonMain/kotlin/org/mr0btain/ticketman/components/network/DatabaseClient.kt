package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.util.network.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.employee.Employee
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.Result

class DatabaseClient (
    private val httpClient: HttpClient
) {
    private var serverUrl = ""

    // For updating the server url
    fun changeServerUrl(url: String) { serverUrl = url }

    suspend fun getCustomerData(): Result<List<CustomerData>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "${serverUrl}/customers/all")
        } catch (ex: UnresolvedAddressException) {
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
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

    suspend fun getTicketData(): Result<List<TicketInfo>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "$serverUrl/tickets/all")
        } catch (ex: UnresolvedAddressException) {
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
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

    suspend fun getEmployeeData(): Result<List<Employee>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "$serverUrl/employees/all")
        } catch (ex: UnresolvedAddressException) {
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            return Result.Error(NetworkError.SERIALIZATION)
        }

        return when (response.status.value) {
            in 200..299 -> {
                val employees = response.body<List<Employee>>()
                Result.Success(employees)
            }

            401 -> Result.Error(NetworkError.UNAUTHORIZED)
            409 -> Result.Error(NetworkError.CONFLICT)
            408 -> Result.Error(NetworkError.REQUEST_TIMEOUT)
            413 -> Result.Error(NetworkError.PAYLOAD_TOO_LARGE)

            in 500..599 -> Result.Error(NetworkError.SERVER_ERROR)

            else -> Result.Error(NetworkError.UNKNOWN)
        }
    }

    suspend fun getInventoryData(): Result<List<InventoryItem>, NetworkError> {
        val response = try {
            httpClient.get(urlString = "$serverUrl/inventory/all")
        } catch (ex: UnresolvedAddressException) {
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
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
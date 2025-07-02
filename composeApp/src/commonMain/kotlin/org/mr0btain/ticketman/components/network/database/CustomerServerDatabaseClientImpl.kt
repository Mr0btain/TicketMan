package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse

class CustomerServerDatabaseClientImpl(
    val client: HttpClient
) : ServerDatabaseClient(
    tag = "CustomerDatabaseClient",
    client = client
) {
    override var serverUrl = ""

    override fun changeUrl(url: String) {
        serverUrl = "$url/api/data/customer"
    }

    override suspend fun sync(data: List<MainDataItem>): List<CustomerData> {
        if (serverUrl.isNotBlank()) {
            val identifiers = data.map { it.identifier }

            networkErrorTryCatch(SYNC_TAG) {
                client.post {
                    url("$serverUrl/sync")
                    contentType(ContentType.Application.Json)
                    setBody(SyncRequest(identifiers))
                }
            }?.let { response -> handleHttpResponse<SyncResponse>(SYNC_TAG, response) }
                ?.onSuccess { syncResponse ->
                    val requested = data.filter { item -> syncResponse.requested.contains(item.identifier) }

                    batchSend(requested)

                    return syncResponse.data.filterIsInstance<CustomerData>()
                }
        }

        return listOf()
    }

    override suspend fun getAll(): List<CustomerData> {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(GET_ALL_TAG) {
                client.get("${serverUrl}/all")
            }?.let { response -> handleHttpResponse<List<CustomerData>>(GET_ALL_TAG, response) }
                ?.onSuccess { value ->
                    // If we reach this then something has gone wrong
                    AppLogger.m(GET_ALL_TAG, SimpleError.SUCCESS)

                    return value
                }
        }

        return emptyList()
    }
}
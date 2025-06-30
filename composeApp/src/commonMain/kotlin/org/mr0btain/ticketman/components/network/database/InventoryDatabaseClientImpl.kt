package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse

class InventoryDatabaseClientImpl(
    val client: HttpClient
) : DatabaseClient(client) {
    override val TAG = "InventoryDatabaseClient"
    override val SYNC_TAG = "${TAG}:Sync"
    override val GET_ALL_TAG = "${TAG}:GetAll"
    override val SEND_TAG = "$TAG:Send"
    override val BATCH_SEND_TAG = "$TAG:BatchSend"
    override val UPDATE_TAG = "$TAG:Update"
    override val REMOVE_TAG = "$TAG:Remove"

    override var serverUrl = ""

    override fun changeUrl(url: String) {
        serverUrl = "$url/api/data/inventory"
    }

    override suspend fun sync(data: List<MainDataItem>): List<InventoryItem> {
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

                return syncResponse.data.filterIsInstance<InventoryItem>()
            }

        return listOf()
    }

    override suspend fun getAll(): List<InventoryItem> {
        networkErrorTryCatch(GET_ALL_TAG) {
            client.get("${serverUrl}/all")
        }?.let { response -> handleHttpResponse<List<InventoryItem>>(GET_ALL_TAG, response) }
            ?.onSuccess { value ->
                // If we reach this then something has gone wrong
                AppLogger.m(GET_ALL_TAG, SimpleError.SUCCESS)

                return value
            }

        return emptyList()
    }
}
package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.request.StockChangeRequest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class InventoryRemoteRepository(
    client: HttpClient
) : RemoteRepository<Inventory>(
    tag = "InventoryRemoteRepository",
    client = client,
    route = "api/data/inventory",
    types = itemTypes<Inventory>()
) {
    private val GET_IMAGES_TAG = "$tag:GetImages"
    private val SET_IMAGES_TAG = "$tag:SetImages"
    private val SET_STOCK_TAG = "$tag:SetStock"

    /** Photos of the item itself, empty on any failure */
    suspend fun getImages(identifier: Uuid): List<StoredImage> {
        if (serverUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(GET_IMAGES_TAG) {
            client.get { url("$serverUrl/get/images/$identifier") }
        } ?: return emptyList()

        handleHttpResponse<List<StoredImage>>(GET_IMAGES_TAG, response).onSuccess { return it }
        return emptyList()
    }

    /** Makes the item's photos exactly [images]. New ones must have been uploaded first */
    suspend fun setImages(identifier: Uuid, images: List<StoredImage>): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(SET_IMAGES_TAG) {
            client.post {
                url("$serverUrl/set/images/$identifier")
                contentType(ContentType.Application.Json)
                setBody(images)
            }
        }

        return handleActionHttpResponse(SET_IMAGES_TAG, response)
    }

    /**
     * Sets the stock count, which [update] leaves alone. Refused with a conflict if it isn't [from] any more,
     * like when a pickup took some since the item was opened
     */
    suspend fun setStock(identifier: Uuid, from: Int, to: Int): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(SET_STOCK_TAG) {
            client.post {
                url("$serverUrl/set/stock/$identifier")
                contentType(ContentType.Application.Json)
                setBody(StockChangeRequest(from, to))
            }
        }

        return handleActionHttpResponse(SET_STOCK_TAG, response)
    }
}

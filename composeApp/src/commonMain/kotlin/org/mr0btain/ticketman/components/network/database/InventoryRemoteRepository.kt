package org.mr0btain.ticketman.components.network.database

import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.error.TmResult
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
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.InventoryPaths
import org.mr0btain.ticketman.server.fill
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
    route = ApiRoutes.INVENTORY,
    types = itemTypes<Inventory>()
) {
    private val GET_IMAGES_TAG = "$tag:GetImages"
    private val SET_IMAGES_TAG = "$tag:SetImages"
    private val SET_STOCK_TAG = "$tag:SetStock"
    private val CODE_TAG = "$tag:Code"

    /** The item with exactly this barcode or SKU, `NOT_FOUND` when there's none */
    suspend fun getByCode(code: String): TmResult<Inventory, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(CODE_TAG) {
            client.get { url("$serverUrl${InventoryPaths.BY_CODE.fill(code.trim().encodeURLPathPart())}") }
        }

        return handleDataHttpResponse(CODE_TAG, response)
    }

    /** Every category in use, by name */
    suspend fun categories(): TmResult<List<String>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(CODE_TAG) {
            client.get { url("$serverUrl${InventoryPaths.CATEGORIES}") }
        }

        return handleDataHttpResponse(CODE_TAG, response)
    }

    /** Photos of the item itself */
    suspend fun getImages(identifier: Uuid): TmResult<List<StoredImage>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(GET_IMAGES_TAG) {
            client.get { url("$serverUrl${InventoryPaths.GET_IMAGES.fill(identifier)}") }
        }

        return handleDataHttpResponse(GET_IMAGES_TAG, response)
    }

    /** Makes the item's photos exactly [images]. New ones must have been uploaded first */
    suspend fun setImages(identifier: Uuid, images: List<StoredImage>): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(SET_IMAGES_TAG) {
            client.post {
                url("$serverUrl${InventoryPaths.SET_IMAGES.fill(identifier)}")
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
    /** The count at [location], where this device works for null */
    suspend fun setStock(identifier: Uuid, from: Int, to: Int, location: Uuid? = null): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(SET_STOCK_TAG) {
            client.post {
                url("$serverUrl${InventoryPaths.SET_STOCK.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(StockChangeRequest(from, to, location))
            }
        }

        return handleActionHttpResponse(SET_STOCK_TAG, response)
    }
}

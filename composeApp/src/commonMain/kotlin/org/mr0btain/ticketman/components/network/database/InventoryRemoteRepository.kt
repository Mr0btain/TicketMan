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
}

package org.mr0btain.ticketman.components.network.image

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.database.transferTimeout
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.NetworkError
import kotlin.uuid.Uuid

/**
 * Client for /api/image: uploading photos and building the urls they're fetched from
 */
@Suppress("PrivatePropertyName")
class ImageRemoteRepository(
    private val client: HttpClient
) {
    private val TAG = "ImageRemoteRepository"
    private val UPLOAD_TAG = "$TAG:Upload"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = value.trimEnd('/')
        }

    private val imageUrl get() = "$baseUrl/api/image"

    /** Where a photo's [variant] is fetched from, null with no server set */
    fun urlFor(identifier: Uuid, variant: ImageVariant): String? =
        if (baseUrl.isBlank()) null else "$imageUrl/$identifier/${variant.path}"

    /**
     * Sends a photo. It comes back pending, and is only kept once a note or item is saved with it
     * @param onProgress Bytes sent so far and the total, called as the upload goes
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        description: String = "",
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> }
    ): Result<StoredImage, ServerError> {
        if (baseUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        if (bytes.size > ImageLimits.MAX_UPLOAD_BYTES) {
            return Result.Error(ServerError(NetworkError.PAYLOAD_TOO_LARGE, "That photo is too big"))
        }

        val response = networkErrorTryCatch(UPLOAD_TAG) {
            client.submitFormWithBinaryData(
                url = "$imageUrl/upload",
                formData = formData {
                    if (description.isNotBlank()) append(ImageLimits.DESCRIPTION_FIELD, description)
                    append(ImageLimits.UPLOAD_FIELD, bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"${fileName.safeFileName()}\"")
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    })
                }
            ) {
                transferTimeout()
                onUpload { sent, total -> onProgress(sent, total) }
            }
        }

        return handleDataHttpResponse(UPLOAD_TAG, response)
    }

    // Quotes or line breaks would break the header, the server doesn't use the name anyway
    private fun String.safeFileName(): String =
        filter { it.isLetterOrDigit() || it in "._- " }.ifBlank { "photo" }
}

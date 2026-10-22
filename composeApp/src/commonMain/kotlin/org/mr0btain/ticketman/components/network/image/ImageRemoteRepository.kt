package org.mr0btain.ticketman.components.network.image

import org.mr0btain.ticketman.components.network.database.ServerRepository
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.ImagePaths
import org.mr0btain.ticketman.server.fill
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.database.transferTimeout
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import kotlin.uuid.Uuid

/**
 * Client for /api/image: uploading photos and building the urls they're fetched from
 */
@Suppress("PrivatePropertyName")
class ImageRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "ImageRemoteRepository"
    private val UPLOAD_TAG = "$TAG:Upload"
    private val BYTES_TAG = "$TAG:Bytes"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = value.trimEnd('/')
        }

    private val imageUrl get() = "$baseUrl${ApiRoutes.IMAGE}"

    /** Where a photo's [variant] is fetched from, null with no server set */
    fun urlFor(identifier: Uuid, variant: ImageVariant): String? =
        if (baseUrl.isBlank()) null else "$imageUrl${ImagePaths.FILE.fill(identifier, variant.path)}"

    /** A photo's [variant] as JPEG bytes, for printing it. Small enough to read whole */
    suspend fun bytes(identifier: Uuid, variant: ImageVariant): TmResult<ByteArray, ServerError> {
        val url = urlFor(identifier, variant) ?: return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(BYTES_TAG, networkRequest(BYTES_TAG) { client.get(url) })
    }

    /**
     * Sends a photo. It comes back pending, and is only kept once a note or item is saved with it
     * @param onProgress Bytes sent so far and the total, called as the upload goes
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        description: String = "",
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> }
    ): TmResult<StoredImage, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        if (bytes.size > ImageLimits.MAX_UPLOAD_BYTES) {
            return TmResult.Error(ServerError(NetworkError.PAYLOAD_TOO_LARGE, "That photo is too big"))
        }

        val response = networkRequest(UPLOAD_TAG) {
            client.submitFormWithBinaryData(
                url = "$imageUrl${ImagePaths.UPLOAD}",
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

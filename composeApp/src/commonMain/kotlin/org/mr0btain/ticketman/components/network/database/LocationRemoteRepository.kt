package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.LocationPaths
import org.mr0btain.ticketman.server.NetworkError

/**
 * Client for the shop's locations under /api/location. Anyone logged in reads them, admins add and change them
 */
@Suppress("PrivatePropertyName")
class LocationRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "LocationRemoteRepository"
    private val LIST_TAG = "$TAG:List"
    private val SAVE_TAG = "$TAG:Save"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.LOCATION}"
        }

    /** Every location, retired ones too, main first */
    suspend fun all(): TmResult<List<Location>, ServerError> =
        data(LIST_TAG) { client.get { url(baseUrl) } }

    /** Admin only. Answers with it as saved */
    suspend fun add(location: Location): TmResult<Location, ServerError> =
        data(SAVE_TAG) {
            client.post {
                url("$baseUrl${LocationPaths.ADD}")
                contentType(ContentType.Application.Json)
                setBody(location)
            }
        }

    /** Admin only. Answers with it as saved */
    suspend fun update(location: Location): TmResult<Location, ServerError> =
        data(SAVE_TAG) {
            client.post {
                url("$baseUrl${LocationPaths.UPDATE}")
                contentType(ContentType.Application.Json)
                setBody(location)
            }
        }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}

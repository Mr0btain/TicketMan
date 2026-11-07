package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.catalog.CatalogModel
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.CatalogPaths
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Client for the repair menu under /api/catalog. Anyone logged in reads it, admins change it
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class CatalogRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "CatalogRemoteRepository"
    private val LIST_TAG = "$TAG:List"
    private val SAVE_TAG = "$TAG:Save"
    private val REMOVE_TAG = "$TAG:Remove"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.CATALOG}"
        }

    /** The whole menu, by manufacturer then model */
    suspend fun all(): TmResult<List<CatalogModel>, ServerError> =
        data(LIST_TAG) { client.get { url(baseUrl) } }

    /** Admin only. Answers with it as saved */
    suspend fun add(model: CatalogModel): TmResult<CatalogModel, ServerError> =
        data(SAVE_TAG) {
            client.post {
                url("$baseUrl${CatalogPaths.ADD}")
                contentType(ContentType.Application.Json)
                setBody(model)
            }
        }

    /** Admin only, its repairs replacing the ones it had. Answers with it as saved */
    suspend fun update(model: CatalogModel): TmResult<CatalogModel, ServerError> =
        data(SAVE_TAG) {
            client.post {
                url("$baseUrl${CatalogPaths.UPDATE}")
                contentType(ContentType.Application.Json)
                setBody(model)
            }
        }

    /** Admin only */
    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleActionHttpResponse(REMOVE_TAG, networkRequest(REMOVE_TAG) {
            client.delete { url("$baseUrl${CatalogPaths.REMOVE.fill(identifier)}") }
        })
    }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}

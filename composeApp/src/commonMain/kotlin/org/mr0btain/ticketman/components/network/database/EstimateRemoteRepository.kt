package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.EstimatePaths
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.EstimateDecisionRequest
import org.mr0btain.ticketman.server.request.EstimateRequest
import kotlin.uuid.Uuid

/**
 * Client for estimates under /api/estimate
 */
@Suppress("PrivatePropertyName")
class EstimateRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "EstimateRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.ESTIMATE}"
        }

    /** The ticket's estimates, newest first */
    suspend fun forTicket(ticketIdentifier: Uuid): TmResult<List<Estimate>, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${EstimatePaths.TICKET.fill(ticketIdentifier)}") } }

    /** Sends a new one, withdrawing one still waiting */
    suspend fun send(ticketIdentifier: Uuid, request: EstimateRequest): TmResult<Estimate, ServerError> =
        data(CHANGE_TAG) {
            client.post {
                url("$baseUrl${EstimatePaths.TICKET.fill(ticketIdentifier)}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** What the customer said, by phone or at the counter */
    suspend fun decide(identifier: Uuid, request: EstimateDecisionRequest): TmResult<Estimate, ServerError> =
        data(CHANGE_TAG) {
            client.post {
                url("$baseUrl${EstimatePaths.DECIDE.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun withdraw(identifier: Uuid): TmResult<Estimate, ServerError> =
        data(CHANGE_TAG) { client.post { url("$baseUrl${EstimatePaths.WITHDRAW.fill(identifier)}") } }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}

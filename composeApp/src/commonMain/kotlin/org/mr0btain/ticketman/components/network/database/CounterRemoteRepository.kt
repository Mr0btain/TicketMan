package org.mr0btain.ticketman.components.network.database

import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.CounterPaths
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.RepeatCheckRequest
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import kotlin.uuid.Uuid

/**
 * Client for the counter's lookups under /api/counter
 */
@Suppress("PrivatePropertyName")
class CounterRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "CounterRemoteRepository"
    private val SEARCH_TAG = "$TAG:Search"
    private val QUEUE_TAG = "$TAG:Queue"
    private val REPEAT_TAG = "$TAG:RepeatCheck"
    private val PICKUP_TAG = "$TAG:Pickup"
    private val CHECK_IN_TAG = "$TAG:CheckIn"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.COUNTER}"
        }

    /** Tickets and customers matching [query]. Nothing for a blank one */
    suspend fun search(query: String, limit: Int): TmResult<CounterSearchResponse, ServerError> {
        if (query.isBlank()) return TmResult.Success(CounterSearchResponse())
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(SEARCH_TAG) {
            client.get {
                url("$baseUrl${CounterPaths.SEARCH}")
                parameter(ApiQuery.QUERY, query)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

        return handleDataHttpResponse(SEARCH_TAG, response)
    }

    /** Null when it couldn't be loaded, so the screen can tell that apart from an empty queue */
    suspend fun queue(recentLimit: Int): CounterQueueResponse? {
        if (baseUrl.isBlank()) return null

        val response = networkErrorTryCatch(QUEUE_TAG) {
            client.get { url("$baseUrl${CounterPaths.QUEUE}?recent=$recentLimit") }
        } ?: return null

        handleHttpResponse<CounterQueueResponse>(QUEUE_TAG, response).onSuccess { return it }
        return null
    }

    /** Checks a device in: ticket, customer, tech, deposit and signature all at once */
    suspend fun checkIn(request: CheckInRequest): TmResult<CheckInResponse, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(CHECK_IN_TAG) {
            client.post {
                url("$baseUrl${CounterPaths.CHECK_IN}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        return handleDataHttpResponse(CHECK_IN_TAG, response)
    }

    /**
     * Hands a ticket back: takes the payments, pays off its invoice and closes the ticket.
     * Refused with what's still owed if the payments don't cover it
     */
    suspend fun pickup(ticketIdentifier: Uuid, payments: List<PaymentRequest>): TmResult<Invoice, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(PICKUP_TAG) {
            client.post {
                url("$baseUrl${CounterPaths.PICKUP}")
                contentType(ContentType.Application.Json)
                setBody(PickupRequest(ticketIdentifier, payments))
            }
        }

        return handleDataHttpResponse(PICKUP_TAG, response)
    }

    /** Earlier repairs on this device for the customer, newest first */
    suspend fun repeatCheck(request: RepeatCheckRequest): List<TicketSummary> {
        if (baseUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(REPEAT_TAG) {
            client.post {
                url("$baseUrl${CounterPaths.REPEAT_CHECK}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } ?: return emptyList()

        handleHttpResponse<List<TicketSummary>>(REPEAT_TAG, response).onSuccess { return it }
        return emptyList()
    }
}

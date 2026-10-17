package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.request.RepeatCheckRequest
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse

/**
 * Client for the counter's lookups under /api/counter
 */
@Suppress("PrivatePropertyName")
class CounterRemoteRepository(
    private val client: HttpClient
) {
    private val TAG = "CounterRemoteRepository"
    private val SEARCH_TAG = "$TAG:Search"
    private val QUEUE_TAG = "$TAG:Queue"
    private val REPEAT_TAG = "$TAG:RepeatCheck"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}/api/counter"
        }

    suspend fun search(query: String, limit: Int): CounterSearchResponse {
        if (baseUrl.isBlank() || query.isBlank()) return CounterSearchResponse()

        val response = networkErrorTryCatch(SEARCH_TAG) {
            client.post {
                url("$baseUrl/search")
                contentType(ContentType.Application.Json)
                setBody(SearchQuery(query, limit))
            }
        } ?: return CounterSearchResponse()

        handleHttpResponse<CounterSearchResponse>(SEARCH_TAG, response).onSuccess { return it }
        return CounterSearchResponse()
    }

    /** Null when it couldn't be loaded, so the screen can tell that apart from an empty queue */
    suspend fun queue(recentLimit: Int): CounterQueueResponse? {
        if (baseUrl.isBlank()) return null

        val response = networkErrorTryCatch(QUEUE_TAG) {
            client.get { url("$baseUrl/queue?recent=$recentLimit") }
        } ?: return null

        handleHttpResponse<CounterQueueResponse>(QUEUE_TAG, response).onSuccess { return it }
        return null
    }

    /** Earlier repairs on this device for the customer, newest first */
    suspend fun repeatCheck(request: RepeatCheckRequest): List<TicketSummary> {
        if (baseUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(REPEAT_TAG) {
            client.post {
                url("$baseUrl/repeat-check")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } ?: return emptyList()

        handleHttpResponse<List<TicketSummary>>(REPEAT_TAG, response).onSuccess { return it }
        return emptyList()
    }
}

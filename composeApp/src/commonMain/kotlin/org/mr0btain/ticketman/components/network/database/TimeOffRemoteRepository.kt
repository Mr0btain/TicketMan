package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.schedule.MyTimeOff
import org.mr0btain.ticketman.data.schedule.TimeOffAllowance
import org.mr0btain.ticketman.data.schedule.TimeOffBalance
import org.mr0btain.ticketman.data.schedule.TimeOffDecision
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.TimeOffPaths
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.Uuid

/**
 * Client for time off under /api/timeoff: asking for it and seeing your own, and admins answering and setting
 * allowances
 */
@Suppress("PrivatePropertyName")
class TimeOffRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "TimeOffRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.TIME_OFF}"
        }

    /** The caller's balance for [year] (this year when null) and their requests */
    suspend fun mine(year: Int? = null): TmResult<MyTimeOff, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${TimeOffPaths.ME}"); year?.let { parameter("year", it) } } }

    suspend fun request(request: TimeOffRequest): TmResult<TimeOffRequest, ServerError> =
        data(CHANGE_TAG) { client.post { body(TimeOffPaths.REQUEST, request) } }

    suspend fun withdraw(identifier: Uuid): TmResult<TimeOffRequest, ServerError> =
        data(CHANGE_TAG) { client.post { url("$baseUrl${TimeOffPaths.WITHDRAW.fill(identifier)}") } }

    /** Admin only, only [status]'s when given */
    suspend fun requests(status: TimeOffStatus? = null): TmResult<List<TimeOffRequest>, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${TimeOffPaths.REQUESTS}"); status?.let { parameter("status", it.name) } } }

    /** Admin only */
    suspend fun approve(identifier: Uuid): TmResult<TimeOffRequest, ServerError> =
        data(CHANGE_TAG) { client.post { body(TimeOffPaths.APPROVE.fill(identifier), TimeOffDecision()) } }

    /** Admin only */
    suspend fun decline(identifier: Uuid, reason: String): TmResult<TimeOffRequest, ServerError> =
        data(CHANGE_TAG) { client.post { body(TimeOffPaths.DECLINE.fill(identifier), TimeOffDecision(reason)) } }

    /** Admin only */
    suspend fun balances(year: Int? = null): TmResult<List<TimeOffBalance>, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${TimeOffPaths.BALANCES}"); year?.let { parameter("year", it) } } }

    /** Admin only */
    suspend fun setAllowance(user: Uuid, hours: Double): TmResult<TimeOffBalance, ServerError> =
        data(CHANGE_TAG) { client.post { body(TimeOffPaths.ALLOWANCE.fill(user), TimeOffAllowance(hours)) } }

    private inline fun <reified T> HttpRequestBuilder.body(path: String, value: T) {
        url("$baseUrl$path")
        contentType(ContentType.Application.Json)
        setBody(value)
    }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}

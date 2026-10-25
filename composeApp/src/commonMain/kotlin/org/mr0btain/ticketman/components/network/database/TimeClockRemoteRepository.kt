package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.timeclock.ClockState
import org.mr0btain.ticketman.data.timeclock.PunchResult
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.timeclock.Timesheet
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.TimeClockPaths
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.Uuid

/**
 * Client for the time clock under /api/timeclock. Clocking by PIN at a station goes without a login, through
 * `ServerUserClient.stationPunch`
 */
@Suppress("PrivatePropertyName")
class TimeClockRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "TimeClockRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val PUNCH_TAG = "$TAG:Punch"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.TIME_CLOCK}"
        }

    /** Whether the signed in user is on the clock */
    suspend fun me(): TmResult<ClockState, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${TimeClockPaths.ME}") } }

    /** Clocks the signed in user in or out */
    suspend fun mePunch(): TmResult<PunchResult, ServerError> =
        data(PUNCH_TAG) { client.post { url("$baseUrl${TimeClockPaths.ME_PUNCH}") } }

    suspend fun myTimesheet(from: String, to: String): TmResult<Timesheet, ServerError> =
        data(GET_TAG) { client.get { days(TimeClockPaths.ME_TIMESHEET, from, to) } }

    /** Who's on the clock now */
    suspend fun now(): TmResult<List<TimeEntry>, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${TimeClockPaths.NOW}") } }

    /** Everyone's time, or only [user]'s. Admin only */
    suspend fun timesheet(from: String, to: String, user: Uuid? = null): TmResult<Timesheet, ServerError> =
        data(GET_TAG) {
            client.get {
                days(TimeClockPaths.TIMESHEET, from, to)
                user?.let { parameter("user", it.toString()) }
            }
        }

    suspend fun add(entry: TimeEntry): TmResult<TimeEntry, ServerError> =
        data(CHANGE_TAG) { client.post { json(TimeClockPaths.ENTRY_ADD, entry) } }

    suspend fun update(entry: TimeEntry): TmResult<TimeEntry, ServerError> =
        data(CHANGE_TAG) { client.post { json(TimeClockPaths.ENTRY_UPDATE, entry) } }

    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleActionHttpResponse(CHANGE_TAG, networkRequest(CHANGE_TAG) { client.delete { url("$baseUrl${TimeClockPaths.ENTRY_REMOVE.fill(identifier)}") } })
    }

    private fun HttpRequestBuilder.days(path: String, from: String, to: String) {
        url("$baseUrl$path")
        parameter("from", from)
        parameter("to", to)
    }

    private fun HttpRequestBuilder.json(path: String, body: Any) {
        url("$baseUrl$path")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}

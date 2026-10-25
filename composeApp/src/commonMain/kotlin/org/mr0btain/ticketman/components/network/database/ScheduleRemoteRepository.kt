package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleRequest
import org.mr0btain.ticketman.data.schedule.ScheduleScope
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.SchedulePaths
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.Uuid

/**
 * Client for the staff schedule under /api/schedule. Anyone logged in reads it, admins write it
 */
@Suppress("PrivatePropertyName")
class ScheduleRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "ScheduleRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.SCHEDULE}"
        }

    /** Every entry touching [from] to [to], days like 2026-10-01, both included, earliest first */
    suspend fun range(from: String, to: String, user: Uuid? = null): TmResult<List<ScheduleEntry>, ServerError> =
        data(GET_TAG) {
            client.get {
                url("$baseUrl${SchedulePaths.RANGE}")
                parameter("from", from)
                parameter("to", to)
                user?.let { parameter("user", it.toString()) }
            }
        }

    /** Admin only. Answers with every entry it made, more than one for a weekly repeat */
    suspend fun add(request: ScheduleRequest): TmResult<List<ScheduleEntry>, ServerError> =
        data(CHANGE_TAG) {
            client.post {
                url("$baseUrl${SchedulePaths.ADD}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Admin only. Answers with every entry changed */
    suspend fun update(entry: ScheduleEntry, scope: ScheduleScope): TmResult<List<ScheduleEntry>, ServerError> =
        data(CHANGE_TAG) {
            client.post {
                url("$baseUrl${SchedulePaths.UPDATE}")
                parameter("scope", scope.name)
                contentType(ContentType.Application.Json)
                setBody(entry)
            }
        }

    /** Admin only */
    suspend fun remove(identifier: Uuid, scope: ScheduleScope): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleActionHttpResponse(CHANGE_TAG, networkRequest(CHANGE_TAG) {
            client.delete {
                url("$baseUrl${SchedulePaths.REMOVE.fill(identifier)}")
                parameter("scope", scope.name)
            }
        })
    }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }
}

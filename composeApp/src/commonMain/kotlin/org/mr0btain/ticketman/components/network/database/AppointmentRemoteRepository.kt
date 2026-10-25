package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.AppointmentPaths
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.AppointmentStatusRequest
import kotlin.uuid.Uuid

/**
 * Client for the appointment calendar under /api/appointment
 */
@Suppress("PrivatePropertyName")
class AppointmentRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "AppointmentRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.APPOINTMENT}"
        }

    /** Starting from [from] to [to], days like 2026-10-01, both included, earliest first */
    suspend fun range(from: String, to: String): TmResult<List<Appointment>, ServerError> =
        data(GET_TAG) {
            client.get {
                url("$baseUrl${AppointmentPaths.RANGE}")
                parameter("from", from)
                parameter("to", to)
            }
        }

    suspend fun get(identifier: Uuid): TmResult<Appointment, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${AppointmentPaths.GET.fill(identifier)}") } }

    suspend fun add(appointment: Appointment): TmResult<Appointment, ServerError> =
        data(CHANGE_TAG) { client.post { json(AppointmentPaths.ADD, appointment) } }

    suspend fun update(appointment: Appointment): TmResult<Appointment, ServerError> =
        data(CHANGE_TAG) { client.post { json(AppointmentPaths.UPDATE, appointment) } }

    suspend fun setStatus(identifier: Uuid, status: AppointmentStatus): TmResult<Appointment, ServerError> =
        data(CHANGE_TAG) { client.post { json(AppointmentPaths.STATUS.fill(identifier), AppointmentStatusRequest(status)) } }

    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleActionHttpResponse(CHANGE_TAG, networkRequest(CHANGE_TAG) { client.delete { url("$baseUrl${AppointmentPaths.REMOVE.fill(identifier)}") } })
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

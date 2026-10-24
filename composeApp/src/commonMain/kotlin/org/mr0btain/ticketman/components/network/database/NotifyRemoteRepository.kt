package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.notify.NotificationSettings
import org.mr0btain.ticketman.data.notify.OutboxMessage
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.NotifyPaths
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.TestMessageRequest
import org.mr0btain.ticketman.server.request.TicketMessageRequest
import kotlin.uuid.Uuid

/**
 * Client for messages to customers under /api/notify. The settings never carry the mail password or text token
 * back; saving with them left null keeps the saved ones
 */
@Suppress("PrivatePropertyName")
class NotifyRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "NotifyRemoteRepository"
    private val SETTINGS_TAG = "$TAG:Settings"
    private val OUTBOX_TAG = "$TAG:Outbox"
    private val SEND_TAG = "$TAG:Send"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.NOTIFY}"
        }

    /** Admin only */
    suspend fun settings(): TmResult<NotificationSettings, ServerError> =
        data(SETTINGS_TAG) { client.get { url("$baseUrl${NotifyPaths.SETTINGS}") } }

    /** Admin only. Answers with the settings as saved, without the secrets */
    suspend fun saveSettings(settings: NotificationSettings): TmResult<NotificationSettings, ServerError> =
        data(SETTINGS_TAG) {
            client.post {
                url("$baseUrl${NotifyPaths.SETTINGS}")
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
        }

    /** Admin only: sends a test now with these settings, which needn't be saved yet */
    suspend fun test(request: TestMessageRequest): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return noServer()

        return handleActionHttpResponse(SEND_TAG, networkRequest(SEND_TAG) {
            client.post {
                url("$baseUrl${NotifyPaths.TEST}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        })
    }

    /** Admin only: every message, newest first */
    suspend fun outbox(offset: Long, limit: Int): TmResult<List<OutboxMessage>, ServerError> =
        data(OUTBOX_TAG) {
            client.get {
                url("$baseUrl${NotifyPaths.OUTBOX}")
                parameter(ApiQuery.OFFSET, offset)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

    /** Admin only */
    suspend fun retry(identifier: Uuid): TmResult<OutboxMessage, ServerError> =
        data(OUTBOX_TAG) { client.post { url("$baseUrl${NotifyPaths.RETRY.fill(identifier)}") } }

    /** Admin only */
    suspend fun cancel(identifier: Uuid): TmResult<OutboxMessage, ServerError> =
        data(OUTBOX_TAG) { client.post { url("$baseUrl${NotifyPaths.CANCEL.fill(identifier)}") } }

    /** What the ticket's customer was sent, newest first */
    suspend fun forTicket(ticketIdentifier: Uuid): TmResult<List<OutboxMessage>, ServerError> =
        data(OUTBOX_TAG) { client.get { url("$baseUrl${NotifyPaths.TICKET.fill(ticketIdentifier)}") } }

    /** Sends a message to the ticket's customer now. Answers with one per channel it went by */
    suspend fun sendForTicket(ticketIdentifier: Uuid, request: TicketMessageRequest = TicketMessageRequest()): TmResult<List<OutboxMessage>, ServerError> =
        data(SEND_TAG) {
            client.post {
                url("$baseUrl${NotifyPaths.TICKET_SEND.fill(ticketIdentifier)}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return noServer()
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }

    private fun noServer() = TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
}

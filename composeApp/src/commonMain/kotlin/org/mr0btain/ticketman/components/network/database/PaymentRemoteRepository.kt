package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentOptions
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.PaymentPaths
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.ChargeRefundRequest
import org.mr0btain.ticketman.server.request.PaymentLinkRequest
import org.mr0btain.ticketman.server.request.ReaderChargeRequest
import kotlin.uuid.Uuid

/**
 * Client for card payments under /api/payment. The settings never carry the processor's keys back; saving with
 * them left null keeps the saved ones
 */
@Suppress("PrivatePropertyName")
class PaymentRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "PaymentRemoteRepository"
    private val SETTINGS_TAG = "$TAG:Settings"
    private val CHARGE_TAG = "$TAG:Charge"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.PAYMENT}"
        }

    /** Admin only */
    suspend fun settings(): TmResult<PaymentSettings, ServerError> =
        data(SETTINGS_TAG) { client.get { url("$baseUrl${PaymentPaths.SETTINGS}") } }

    /** Admin only. Answers with the settings as saved, without the keys */
    suspend fun saveSettings(settings: PaymentSettings): TmResult<PaymentSettings, ServerError> =
        data(SETTINGS_TAG) {
            client.post {
                url("$baseUrl${PaymentPaths.SETTINGS}")
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
        }

    /** Admin only: the readers on the processor account, with the saved keys */
    suspend fun accountReaders(): TmResult<List<CardReader>, ServerError> =
        data(SETTINGS_TAG) { client.get { url("$baseUrl${PaymentPaths.ACCOUNT_READERS}") } }

    /** What the counter can offer */
    suspend fun options(): TmResult<PaymentOptions, ServerError> =
        data(SETTINGS_TAG) { client.get { url("$baseUrl${PaymentPaths.OPTIONS}") } }

    suspend fun startReader(request: ReaderChargeRequest): TmResult<CardCharge, ServerError> =
        data(CHARGE_TAG) {
            client.post {
                url("$baseUrl${PaymentPaths.READER}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun link(request: PaymentLinkRequest): TmResult<CardCharge, ServerError> =
        data(CHARGE_TAG) {
            client.post {
                url("$baseUrl${PaymentPaths.LINK}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** The charge, which the server asks the processor about first while it's waiting */
    suspend fun charge(identifier: Uuid): TmResult<CardCharge, ServerError> =
        data(CHARGE_TAG) { client.get { url("$baseUrl${PaymentPaths.CHARGE.fill(identifier)}") } }

    suspend fun cancel(identifier: Uuid): TmResult<CardCharge, ServerError> =
        data(CHARGE_TAG) { client.post { url("$baseUrl${PaymentPaths.CANCEL.fill(identifier)}") } }

    /** Admin only: gives back a paid charge no invoice has */
    suspend fun refund(identifier: Uuid, reason: String): TmResult<CardCharge, ServerError> =
        data(CHARGE_TAG) {
            client.post {
                url("$baseUrl${PaymentPaths.REFUND.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(ChargeRefundRequest(reason))
            }
        }

    /** Admin only: every charge, newest first */
    suspend fun charges(offset: Long, limit: Int): TmResult<List<CardCharge>, ServerError> =
        data(CHARGE_TAG) {
            client.get {
                url("$baseUrl${PaymentPaths.CHARGES}")
                parameter(ApiQuery.OFFSET, offset)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return noServer()
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }

    private fun noServer() = TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
}

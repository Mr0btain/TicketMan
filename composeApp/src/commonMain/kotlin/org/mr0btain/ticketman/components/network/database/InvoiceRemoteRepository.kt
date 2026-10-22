package org.mr0btain.ticketman.components.network.database

import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.InvoicePaths
import org.mr0btain.ticketman.server.fill
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.error.EmptyResult
import kotlin.uuid.Uuid

/**
 * Client for invoices under /api/invoice. Totals always come from the server
 */
@Suppress("PrivatePropertyName")
class InvoiceRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "InvoiceRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"
    private val FILE_TAG = "$TAG:File"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.INVOICE}"
        }

    /** The ticket's invoice if it has one, doesn't make one */
    suspend fun getForTicket(ticketIdentifier: Uuid): Invoice? {
        if (baseUrl.isBlank()) return null

        val response = networkErrorTryCatch(GET_TAG) {
            client.get { url("$baseUrl${InvoicePaths.FOR_TICKET.fill(ticketIdentifier)}") }
        } ?: return null

        handleHttpResponse<Invoice>(GET_TAG, response).onSuccess { return it }
        return null
    }

    /** The ticket's invoice, made if it doesn't have one */
    suspend fun openForTicket(ticketIdentifier: Uuid): TmResult<Invoice, ServerError> =
        change { client.post { url("$baseUrl${InvoicePaths.FOR_TICKET.fill(ticketIdentifier)}") } }

    /**
     * Saves what changed on an open invoice since it was opened (see `InvoiceChanges.between`), so lines added on the
     * server meanwhile stay. A line someone else changed too answers `CONFLICT`
     */
    suspend fun update(identifier: Uuid, changes: InvoiceChangeRequest): TmResult<Invoice, ServerError> =
        change {
            client.post {
                url("$baseUrl${InvoicePaths.UPDATE.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(changes)
            }
        }

    suspend fun addPayment(identifier: Uuid, payment: PaymentRequest): TmResult<Invoice, ServerError> =
        change {
            client.post {
                url("$baseUrl${InvoicePaths.PAYMENTS.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(payment)
            }
        }

    suspend fun removePayment(identifier: Uuid, paymentIdentifier: Uuid): TmResult<Invoice, ServerError> =
        change { client.delete { url("$baseUrl${InvoicePaths.PAYMENT.fill(identifier, paymentIdentifier)}") } }

    /** Admin only */
    suspend fun void(identifier: Uuid): TmResult<Invoice, ServerError> =
        change { client.post { url("$baseUrl${InvoicePaths.VOID.fill(identifier)}") } }

    /** Admin only: deletes an invoice kept after its ticket was deleted */
    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleActionHttpResponse(CHANGE_TAG, networkRequest(CHANGE_TAG) {
            client.delete { url("$baseUrl${InvoicePaths.REMOVE.fill(identifier)}") }
        })
    }

    /** Newest first */
    suspend fun fetch(offset: Long, limit: Int): TmResult<List<Invoice>, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(GET_TAG) {
            client.get {
                url("$baseUrl${InvoicePaths.FETCH}")
                parameter(ApiQuery.OFFSET, offset)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

        return handleDataHttpResponse(GET_TAG, response)
    }

    /**
     * The invoice as a PDF, made on the server. Read whole: it's a few KB, and it needs the login header, which a
     * browser download wouldn't send
     */
    suspend fun pdf(identifier: Uuid): TmResult<ByteArray, ServerError> =
        file(FILE_TAG) { client.get { url("$baseUrl${InvoicePaths.PDF.fill(identifier)}") } }

    /**
     * Invoices made between two days as CSV, admin only
     * @param from First day, like 2026-10-01
     * @param to Last day, included
     */
    suspend fun export(from: String, to: String): TmResult<ByteArray, ServerError> =
        file(FILE_TAG) {
            client.get {
                url("$baseUrl${InvoicePaths.EXPORT}")
                parameter("from", from)
                parameter("to", to)
            }
        }

    private suspend fun file(tag: String, request: suspend () -> HttpResponse): TmResult<ByteArray, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }

    // Runs a request that answers with the invoice as it now stands
    private suspend fun change(request: suspend () -> HttpResponse): TmResult<Invoice, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(CHANGE_TAG, networkRequest(CHANGE_TAG) { request() })
    }
}

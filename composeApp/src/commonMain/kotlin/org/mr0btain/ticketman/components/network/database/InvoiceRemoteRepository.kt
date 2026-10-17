package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.InvoiceUpdateRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import kotlin.uuid.Uuid

/**
 * Client for invoices under /api/invoice. Totals always come from the server
 */
@Suppress("PrivatePropertyName")
class InvoiceRemoteRepository(
    private val client: HttpClient
) {
    private val TAG = "InvoiceRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}/api/invoice"
        }

    /** The ticket's invoice if it has one, doesn't make one */
    suspend fun getForTicket(ticketIdentifier: Uuid): Invoice? {
        if (baseUrl.isBlank()) return null

        val response = networkErrorTryCatch(GET_TAG) {
            client.get { url("$baseUrl/ticket/$ticketIdentifier") }
        } ?: return null

        handleHttpResponse<Invoice>(GET_TAG, response).onSuccess { return it }
        return null
    }

    /** The ticket's invoice, made if it doesn't have one */
    suspend fun openForTicket(ticketIdentifier: Uuid): Result<Invoice, ServerError> =
        change { client.post { url("$baseUrl/ticket/$ticketIdentifier") } }

    suspend fun update(identifier: Uuid, charges: List<Charge>, discountCents: Long): Result<Invoice, ServerError> =
        change {
            client.post {
                url("$baseUrl/$identifier/update")
                contentType(ContentType.Application.Json)
                setBody(InvoiceUpdateRequest(charges, discountCents))
            }
        }

    suspend fun addPayment(identifier: Uuid, payment: PaymentRequest): Result<Invoice, ServerError> =
        change {
            client.post {
                url("$baseUrl/$identifier/payments")
                contentType(ContentType.Application.Json)
                setBody(payment)
            }
        }

    suspend fun removePayment(identifier: Uuid, paymentIdentifier: Uuid): Result<Invoice, ServerError> =
        change { client.delete { url("$baseUrl/$identifier/payments/$paymentIdentifier") } }

    /** Admin only */
    suspend fun void(identifier: Uuid): Result<Invoice, ServerError> =
        change { client.post { url("$baseUrl/$identifier/void") } }

    /** Newest first */
    suspend fun fetch(offset: Long, limit: Int): List<Invoice> {
        if (baseUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(GET_TAG) {
            client.post {
                url("$baseUrl/fetch")
                contentType(ContentType.Application.Json)
                setBody(PagedRequest(offset, limit))
            }
        } ?: return emptyList()

        handleHttpResponse<List<Invoice>>(GET_TAG, response).onSuccess { return it }
        return emptyList()
    }

    // Runs a request that answers with the invoice as it now stands
    private suspend fun change(request: suspend () -> HttpResponse): Result<Invoice, ServerError> {
        if (baseUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(CHANGE_TAG, networkErrorTryCatch(CHANGE_TAG) { request() })
    }
}

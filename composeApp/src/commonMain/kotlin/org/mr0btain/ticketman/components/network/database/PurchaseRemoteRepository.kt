package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.PurchasePaths
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.OrderPartRequest
import org.mr0btain.ticketman.server.request.PurchaseOrderRequest
import org.mr0btain.ticketman.server.request.ReceiveRequest
import kotlin.uuid.Uuid

/**
 * Client for suppliers and purchase orders under /api/purchase
 */
@Suppress("PrivatePropertyName")
class PurchaseRemoteRepository(
    private val client: HttpClient
) : ServerRepository {
    private val TAG = "PurchaseRemoteRepository"
    private val GET_TAG = "$TAG:Get"
    private val CHANGE_TAG = "$TAG:Change"

    private var baseUrl = ""

    override var serverUrl: String
        get() = baseUrl
        set(value) {
            baseUrl = if (value.isBlank()) "" else "${value.trimEnd('/')}${ApiRoutes.PURCHASE}"
        }

    // ------------------------------------------ Suppliers ------------------------------------------

    suspend fun suppliers(): TmResult<List<Supplier>, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${PurchasePaths.SUPPLIERS}") } }

    suspend fun addSupplier(supplier: Supplier): TmResult<Supplier, ServerError> =
        data(CHANGE_TAG) { client.post { json(PurchasePaths.SUPPLIER_ADD, supplier) } }

    suspend fun updateSupplier(supplier: Supplier): TmResult<Supplier, ServerError> =
        data(CHANGE_TAG) { client.post { json(PurchasePaths.SUPPLIER_UPDATE, supplier) } }

    suspend fun removeSupplier(identifier: Uuid): EmptyResult<ServerError> =
        action { client.delete { url("$baseUrl${PurchasePaths.SUPPLIER.fill(identifier)}") } }

    // ------------------------------------------ Orders ------------------------------------------

    /** Newest first, only [statuses] when given */
    suspend fun orders(offset: Long, limit: Int, statuses: List<PurchaseStatus>? = null): TmResult<List<PurchaseOrder>, ServerError> =
        data(GET_TAG) {
            client.get {
                url("$baseUrl${PurchasePaths.ORDERS}")
                parameter(ApiQuery.OFFSET, offset)
                parameter(ApiQuery.LIMIT, limit)
                statuses?.let { parameter("status", it.joinToString(",") { status -> status.name }) }
            }
        }

    suspend fun order(identifier: Uuid): TmResult<PurchaseOrder, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${PurchasePaths.ORDER.fill(identifier)}") } }

    /** Orders with parts for the ticket */
    suspend fun forTicket(ticketIdentifier: Uuid): TmResult<List<PurchaseOrder>, ServerError> =
        data(GET_TAG) { client.get { url("$baseUrl${PurchasePaths.TICKET.fill(ticketIdentifier)}") } }

    suspend fun add(request: PurchaseOrderRequest): TmResult<PurchaseOrder, ServerError> =
        data(CHANGE_TAG) { client.post { json(PurchasePaths.ADD, request) } }

    suspend fun update(identifier: Uuid, request: PurchaseOrderRequest): TmResult<PurchaseOrder, ServerError> =
        data(CHANGE_TAG) { client.post { json(PurchasePaths.UPDATE.fill(identifier), request) } }

    suspend fun place(identifier: Uuid): TmResult<PurchaseOrder, ServerError> =
        data(CHANGE_TAG) { client.post { url("$baseUrl${PurchasePaths.PLACE.fill(identifier)}") } }

    suspend fun receive(identifier: Uuid, request: ReceiveRequest): TmResult<PurchaseOrder, ServerError> =
        data(CHANGE_TAG) { client.post { json(PurchasePaths.RECEIVE.fill(identifier), request) } }

    suspend fun cancel(identifier: Uuid): TmResult<PurchaseOrder, ServerError> =
        data(CHANGE_TAG) { client.post { url("$baseUrl${PurchasePaths.CANCEL.fill(identifier)}") } }

    /** Deletes a draft */
    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> =
        action { client.delete { url("$baseUrl${PurchasePaths.ORDER.fill(identifier)}") } }

    /** A part for a ticket, onto the supplier's draft or a new one */
    suspend fun orderPart(request: OrderPartRequest): TmResult<PurchaseOrder, ServerError> =
        data(CHANGE_TAG) { client.post { json(PurchasePaths.PART, request) } }

    private fun HttpRequestBuilder.json(path: String, body: Any) {
        url("$baseUrl$path")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified T> data(tag: String, crossinline request: suspend () -> HttpResponse): TmResult<T, ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(tag, networkRequest(tag) { request() })
    }

    private suspend inline fun action(crossinline request: suspend () -> HttpResponse): EmptyResult<ServerError> {
        if (baseUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleActionHttpResponse(CHANGE_TAG, networkRequest(CHANGE_TAG) { request() })
    }
}

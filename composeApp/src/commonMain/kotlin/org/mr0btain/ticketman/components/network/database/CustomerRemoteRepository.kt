package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.customer.CreditEntry
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.CustomerPaths
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.Uuid

class CustomerRemoteRepository(
    httpClient: HttpClient
) : RemoteRepository<Customer>(
    tag = "CustomerRemoteRepository",
    client = httpClient,
    route = ApiRoutes.CUSTOMER,
    types = itemTypes<Customer>()
) {
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val CREDIT_TAG = "$tag:Credit"
    private val INVOICES_TAG = "$tag:Invoices"

    suspend fun addTicket(identifier: Uuid, ticket: Uuid): EmptyResult<ServerError> =
        setEntity(ADD_TICKET_TAG, ticket, "$serverUrl${CustomerPaths.ADD_TICKET.fill(identifier)}")

    /** The customer's tickets, newest first */
    suspend fun getTickets(identifier: Uuid): TmResult<List<Ticket>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(GET_TICKETS_TAG) {
            client.get { url("$serverUrl${CustomerPaths.GET_TICKETS.fill(identifier)}") }
        }

        return handleDataHttpResponse(GET_TICKETS_TAG, response)
    }

    /** Store credit given and spent, newest first */
    suspend fun getCredit(identifier: Uuid): TmResult<List<CreditEntry>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(CREDIT_TAG) {
            client.get { url("$serverUrl${CustomerPaths.CREDIT.fill(identifier)}") }
        }

        return handleDataHttpResponse(CREDIT_TAG, response)
    }

    /** Admin only: gives store credit, or takes it off when negative. Answers with the customer as it is now */
    suspend fun adjustCredit(identifier: Uuid, request: BalanceAdjustRequest): TmResult<Customer, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(CREDIT_TAG) {
            client.post {
                url("$serverUrl${CustomerPaths.CREDIT.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        return handleDataHttpResponse(CREDIT_TAG, response)
    }

    /** The customer's invoices, their tickets' and sales, newest first */
    suspend fun getInvoices(identifier: Uuid): TmResult<List<Invoice>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(INVOICES_TAG) {
            client.get { url("$serverUrl${CustomerPaths.INVOICES.fill(identifier)}") }
        }

        return handleDataHttpResponse(INVOICES_TAG, response)
    }
}

package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.url
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.customer.Customer
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
}

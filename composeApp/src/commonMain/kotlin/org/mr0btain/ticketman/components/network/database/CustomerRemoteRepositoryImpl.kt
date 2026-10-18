package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.onSuccess
import kotlin.uuid.Uuid

class CustomerRemoteRepositoryImpl(
    httpClient: HttpClient
) : RemoteRepository<Customer>(
    tag = "CustomerRemoteRepositoryImpl",
    client = httpClient,
    route = "api/data/customer",
    types = itemTypes<Customer>()
) {
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val GET_TICKETS_TAG = "$tag:GetTickets"

    suspend fun addTicket(identifier: Uuid, ticket: Uuid): EmptyResult<ServerError> =
        setEntity(ADD_TICKET_TAG, ticket, "$serverUrl/add/ticket/$identifier")

    suspend fun getTickets(identifier: Uuid): List<Ticket> {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(GET_TICKETS_TAG) {
                client.get {
                    url("$serverUrl/get/tickets/$identifier")
                    contentType(ContentType.Application.Json)
                    setBody(identifier)
                }
            }

            if (response != null) {
                val result = handleHttpResponse<List<Ticket>>(GET_TICKETS_TAG, response)

                result.onSuccess {
                    return it
                }
            }
        }

        return emptyList()
    }
}
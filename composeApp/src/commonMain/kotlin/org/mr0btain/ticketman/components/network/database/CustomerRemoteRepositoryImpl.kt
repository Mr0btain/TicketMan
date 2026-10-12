package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.handlePlainHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.onSuccess
import kotlin.uuid.Uuid

class CustomerRemoteRepositoryImpl(
    httpClient: HttpClient
) : RemoteRepository<Customer>(
    tag = "CustomerRemoteRepositoryImpl",
    client = httpClient,
    route = "api/data/customer"
) {
    private val ADD_TICKET_TAG = "addTicket"
    private val GET_TICKETS_TAG = "getTickets"

    suspend fun addTicket(identifier: Uuid, ticket: Uuid) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(ADD_TICKET_TAG) {
                client.post {
                    url("$serverUrl/add/ticket/$identifier")
                    contentType(ContentType.Application.Json)
                    setBody(ticket)
                }
            }?.let { response -> handlePlainHttpResponse(ADD_TICKET_TAG, response) }
        }
    }

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
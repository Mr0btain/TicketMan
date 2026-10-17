package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.util.reflect.typeInfo
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.onSuccess
import kotlin.uuid.Uuid

class TicketRemoteRepositoryImpl(
    client: HttpClient
) : RemoteRepository<Ticket>(
    tag = "TicketRemoteRepositoryImpl",
    client = client,
    route = "api/data/ticket",
    itemType = typeInfo<Ticket>(),
    listType = typeInfo<List<Ticket>>()
) {
    private val GET_CUSTOMER_TAG = "$tag:GetCustomer"
    private val SET_CUSTOMER_TAG = "$tag:SetCustomer"
    private val GET_USER_TAG = "$tag:GetUser"
    private val SET_USER_TAG = "$tag:SetUser"
    private val CREATE_TAG = "$tag:Create"
    private val GET_BY_NUMBER_TAG = "$tag:GetByNumber"
    private val GET_HISTORY_TAG = "$tag:GetHistory"
    private val GET_SIGNATURE_TAG = "$tag:GetSignature"

    /** Adds a ticket and returns it as saved, with the number the server gave it */
    suspend fun create(ticket: Ticket): Result<Ticket, ServerError> {
        if (serverUrl.isBlank()) return Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkErrorTryCatch(CREATE_TAG) {
            client.post {
                url("$serverUrl/add")
                contentType(ContentType.Application.Json)
                setBody(ticket)
            }
        }

        return handleDataHttpResponse(CREATE_TAG, response)
    }

    suspend fun getByNumber(number: TicketNumber): Ticket? {
        if (serverUrl.isBlank()) return null

        val response = networkErrorTryCatch(GET_BY_NUMBER_TAG) {
            client.get { url("$serverUrl/number/$number") }
        } ?: return null

        handleHttpResponse<Ticket>(GET_BY_NUMBER_TAG, response).onSuccess { return it }
        return null
    }

    /** Signature from check in, null if there wasn't one */
    suspend fun getSignature(identifier: Uuid): TicketSignature? {
        if (serverUrl.isBlank()) return null

        val response = networkErrorTryCatch(GET_SIGNATURE_TAG) {
            client.get { url("$serverUrl/signature/$identifier") }
        } ?: return null

        handleHttpResponse<TicketSignature>(GET_SIGNATURE_TAG, response).onSuccess { return it }
        return null
    }

    /** Status changes oldest first */
    suspend fun getStatusHistory(identifier: Uuid): List<TicketStatusChange> {
        if (serverUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(GET_HISTORY_TAG) {
            client.get { url("$serverUrl/get/history/$identifier") }
        } ?: return emptyList()

        handleHttpResponse<List<TicketStatusChange>>(GET_HISTORY_TAG, response).onSuccess { return it }
        return emptyList()
    }

    suspend fun getCustomer(identifier: Uuid): EntityReference? =
        getEntity(GET_CUSTOMER_TAG, "$serverUrl/get/customer/$identifier")

    suspend fun setCustomer(identifier: Uuid, customerId: Uuid) =
        setEntity(SET_CUSTOMER_TAG, customerId, "$serverUrl/set/customer/$identifier")

    suspend fun getUser(identifier: Uuid): EntityReference? =
        getEntity(GET_USER_TAG, "$serverUrl/get/user/$identifier")

    suspend fun setUser(identifier: Uuid, userId: Uuid) =
        setEntity(SET_USER_TAG, userId, "$serverUrl/set/user/$identifier")
}
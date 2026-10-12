package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.onSuccess
import kotlin.uuid.Uuid

class TicketRemoteRepositoryImpl(
    client: HttpClient
) : RemoteRepository<Ticket>(
    tag = "TicketRemoteRepositoryImpl",
    client = client,
    route = "api/data/ticket"
) {
    private val GET_CUSTOMER_TAG = "$tag:GetCustomer"
    private val SET_CUSTOMER_TAG = "$tag:SetCustomer"
    private val GET_USER_TAG = "$tag:GetUser"
    private val SET_USER_TAG = "$tag:SetUser"

    suspend fun getCustomer(identifier: Uuid): EntityReference? =
        getEntity(GET_CUSTOMER_TAG, "$serverUrl/get/customer/$identifier")

    suspend fun setCustomer(identifier: Uuid, customerId: Uuid) =
        setEntity(SET_CUSTOMER_TAG, customerId, "$serverUrl/set/customer/$identifier")

    suspend fun getUser(identifier: Uuid): EntityReference? =
        getEntity(GET_USER_TAG, "$serverUrl/get/user/$identifier")

    suspend fun setUser(identifier: Uuid, userId: Uuid) =
        setEntity(SET_USER_TAG, userId, "$serverUrl/set/user/$identifier")
}
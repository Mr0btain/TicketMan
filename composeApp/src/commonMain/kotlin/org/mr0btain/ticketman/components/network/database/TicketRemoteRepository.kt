package org.mr0btain.ticketman.components.network.database

import org.mr0btain.ticketman.server.request.MoveTicketRequest
import io.ktor.client.*
import org.mr0btain.ticketman.server.request.PasscodeBody
import io.ktor.client.request.get
import io.ktor.client.request.delete
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.error.EmptyResult
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.TicketPaths
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.Uuid

class TicketRemoteRepository(
    client: HttpClient
) : RemoteRepository<Ticket>(
    tag = "TicketRemoteRepository",
    client = client,
    route = ApiRoutes.TICKET,
    types = itemTypes<Ticket>()
) {
    private val GET_CUSTOMER_TAG = "$tag:GetCustomer"
    private val SET_CUSTOMER_TAG = "$tag:SetCustomer"
    private val GET_USER_TAG = "$tag:GetUser"
    private val SET_USER_TAG = "$tag:SetUser"
    private val GET_BY_NUMBER_TAG = "$tag:GetByNumber"
    private val GET_HISTORY_TAG = "$tag:GetHistory"
    private val GET_SIGNATURE_TAG = "$tag:GetSignature"
    private val FETCH_MINE_TAG = "$tag:FetchMine"
    private val WORK_TAG = "$tag:Work"
    private val STATUS_TOKEN_TAG = "$tag:StatusToken"
    private val PASSCODE_TAG = "$tag:Passcode"

    /** A page of the tickets assigned to whoever is logged in, newest first */
    suspend fun fetchMine(offset: Long, limit: Int): TmResult<List<Ticket>, ServerError> =
        fetchPage(FETCH_MINE_TAG, TicketPaths.FETCH_MINE, offset, limit)

    suspend fun getByNumber(number: TicketNumber): Ticket? {
        if (serverUrl.isBlank()) return null

        val response = networkErrorTryCatch(GET_BY_NUMBER_TAG) {
            client.get { url("$serverUrl${TicketPaths.BY_NUMBER.fill(number)}") }
        } ?: return null

        handleHttpResponse<Ticket>(GET_BY_NUMBER_TAG, response).onSuccess { return it }
        return null
    }

    /** Signature from check in, null if there wasn't one */
    suspend fun getSignature(identifier: Uuid): TicketSignature? {
        if (serverUrl.isBlank()) return null

        val response = networkErrorTryCatch(GET_SIGNATURE_TAG) {
            client.get { url("$serverUrl${TicketPaths.SIGNATURE.fill(identifier)}") }
        } ?: return null

        handleHttpResponse<TicketSignature>(GET_SIGNATURE_TAG, response).onSuccess { return it }
        return null
    }

    /** Status changes oldest first */
    suspend fun getStatusHistory(identifier: Uuid): List<TicketStatusChange> {
        if (serverUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(GET_HISTORY_TAG) {
            client.get { url("$serverUrl${TicketPaths.HISTORY.fill(identifier)}") }
        } ?: return emptyList()

        handleHttpResponse<List<TicketStatusChange>>(GET_HISTORY_TAG, response).onSuccess { return it }
        return emptyList()
    }

    /** The ticket's customer, `NOT_FOUND` when it has none */
    suspend fun getCustomer(identifier: Uuid): TmResult<EntityReference, ServerError> =
        getEntity(GET_CUSTOMER_TAG, "$serverUrl${TicketPaths.GET_CUSTOMER.fill(identifier)}")

    suspend fun setCustomer(identifier: Uuid, customerId: Uuid) =
        setEntity(SET_CUSTOMER_TAG, customerId, "$serverUrl${TicketPaths.SET_CUSTOMER.fill(identifier)}")

    /** The ticket's technician, `NOT_FOUND` when nobody is assigned */
    suspend fun getUser(identifier: Uuid): TmResult<EntityReference, ServerError> =
        getEntity(GET_USER_TAG, "$serverUrl${TicketPaths.GET_USER.fill(identifier)}")

    suspend fun setUser(identifier: Uuid, userId: Uuid) =
        setEntity(SET_USER_TAG, userId, "$serverUrl${TicketPaths.SET_USER.fill(identifier)}")

    /** The ticket's work log, oldest first */
    suspend fun getWork(identifier: Uuid): TmResult<List<WorkEntry>, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(WORK_TAG, networkRequest(WORK_TAG) { client.get { url("$serverUrl${TicketPaths.GET_WORK.fill(identifier)}") } })
    }

    /** Logs several things at once, all of them or none. Answers with the entries as saved, in the order sent */
    suspend fun addWork(identifier: Uuid, requests: List<WorkRequest>): TmResult<List<WorkEntry>, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(
            WORK_TAG,
            networkRequest(WORK_TAG) {
                client.post {
                    url("$serverUrl${TicketPaths.ADD_WORK_ALL.fill(identifier)}")
                    contentType(ContentType.Application.Json)
                    setBody(requests)
                }
            }
        )
    }

    /** Gives the ticket a new status page link, so the old one stops working. Answers with the ticket as saved */
    suspend fun replaceStatusToken(identifier: Uuid): TmResult<Ticket, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(
            STATUS_TOKEN_TAG,
            networkRequest(STATUS_TOKEN_TAG) { client.post { url("$serverUrl${TicketPaths.STATUS_TOKEN.fill(identifier)}") } }
        )
    }

    /** Sends the ticket to another location. Answers with it there */
    suspend fun moveTo(identifier: Uuid, location: Uuid): TmResult<Ticket, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        return handleDataHttpResponse(
            STATUS_TOKEN_TAG,
            networkRequest(STATUS_TOKEN_TAG) {
                client.post {
                    url("$serverUrl${TicketPaths.MOVE.fill(identifier)}")
                    contentType(ContentType.Application.Json)
                    setBody(MoveTicketRequest(location))
                }
            }
        )
    }

    /** The device's passcode, which the server writes down as looked at. `NOT_FOUND` when there's none */
    suspend fun getPasscode(identifier: Uuid): TmResult<PasscodeBody, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
        return handleDataHttpResponse(PASSCODE_TAG, networkRequest(PASSCODE_TAG) { client.get { url("$serverUrl${TicketPaths.PASSCODE.fill(identifier)}") } })
    }

    suspend fun setPasscode(identifier: Uuid, passcode: String): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError
        return handleActionHttpResponse(PASSCODE_TAG, networkRequest(PASSCODE_TAG) {
            client.post {
                url("$serverUrl${TicketPaths.PASSCODE.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(PasscodeBody(passcode))
            }
        })
    }

    suspend fun clearPasscode(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError
        return handleActionHttpResponse(PASSCODE_TAG, networkRequest(PASSCODE_TAG) { client.delete { url("$serverUrl${TicketPaths.PASSCODE.fill(identifier)}") } })
    }

    /** Takes a work entry off, and its invoice line */
    suspend fun removeWork(workIdentifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        return handleActionHttpResponse(WORK_TAG, networkRequest(WORK_TAG) { client.delete { url("$serverUrl${TicketPaths.REMOVE_WORK.fill(workIdentifier)}") } })
    }
}
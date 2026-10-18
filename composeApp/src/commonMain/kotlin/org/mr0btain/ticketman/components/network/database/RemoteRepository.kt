package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.handlePlainHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import kotlin.uuid.Uuid

/**
 * Base client for a MainItem type's server routes.
 * @param itemType Type info for T, e.g. typeInfo<Customer>()
 * @param listType Type info for List<T>, e.g. typeInfo<List<Customer>>()
 * These are needed because T is erased at runtime, so kotlinx serialization can't find a serializer for it.
 */

// TODO: Im not a fan of the itemtype and listtype but it works for the moment. Will have to fix this later.
@Suppress("PropertyName")
abstract class RemoteRepository<T: MainItem>(
    protected val tag: String,
    protected val client: HttpClient,
    val route: String,
    private val itemType: TypeInfo,
    private val listType: TypeInfo
) {
    protected val FETCH_TAG = "$tag:Fetch"
    protected val SEARCH_TAG = "$tag:Fetch"
    protected val ADD_TAG = "$tag:Add"
    protected val ADD_NOTE_TAG = "$tag:AddNote"
    protected val UPDATE_NOTE_TAG = "$tag:UpdateNote"
    protected val UPDATE_TAG = "$tag:Update"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val REMOVE_NOTE_TAG = "$tag:RemoveNote"

    protected val GET_TAG = "$tag:Get"
    protected val GET_NOTES_TAG = "$tag:GetNotes"

    // Stays blank until a server is set so requests are skipped instead of going to a relative url
    var serverUrl: String = ""
        set(value) {
            field = if (value.isBlank()) "" else "${value.trimEnd('/')}/$route"
        }

    suspend fun add(data: T): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(ADD_TAG) {
            client.post {
                url("$serverUrl/add")
                contentType(ContentType.Application.Json)
                setBody(data, itemType)
            }
        }

        return handleActionHttpResponse(ADD_TAG, response)
    }

    /** Replaces a note's text and photos. Its photos must have been uploaded first */
    suspend fun updateNote(note: Note) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(UPDATE_NOTE_TAG) {
                client.post {
                    url("$serverUrl/update/note/${note.identifier}")
                    contentType(ContentType.Application.Json)
                    setBody(note)
                }
            }?.let { response -> handlePlainHttpResponse(UPDATE_NOTE_TAG, response) }
        }
    }

    suspend fun addNote(identifier: Uuid, note: Note) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(ADD_NOTE_TAG) {
                client.post {
                    url("$serverUrl/add/note/$identifier")
                    contentType(ContentType.Application.Json)
                    setBody(note)
                }
            }?.let { response -> handlePlainHttpResponse(ADD_NOTE_TAG, response) }
        }
    }

    suspend fun fetch(offset: Long, limit: Int): List<T> = fetchPage(FETCH_TAG, "fetch", offset, limit)

    /** A page from one of the route's paged endpoints, empty on any failure */
    protected suspend fun fetchPage(tag: String, path: String, offset: Long, limit: Int): List<T> {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(tag) {
                client.post {
                    url("$serverUrl/$path")
                    contentType(ContentType.Application.Json)
                    setBody(PagedRequest(offset,limit))
                }
            }

            if (response != null) {
                val result = handleHttpResponse<List<T>>(tag, response, listType)

                result.onSuccess {
                    return it
                }
            }
        }

        return emptyList()
    }

    suspend fun get(identifier: Uuid): T? {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(GET_TAG) {
                client.post {
                    url("$serverUrl/get")
                    contentType(ContentType.Application.Json)
                    setBody(identifier)
                }
            }

            if (response != null) {
                val result = handleHttpResponse<T>(GET_TAG, response, itemType)

                result.onSuccess {
                    return it
                }
            }
        }

        return null
    }

    protected suspend fun getEntity(tag: String, url: String): EntityReference? {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(tag) {
                client.get { url(url) }
            }

            if (response != null) {
                val result = handleHttpResponse<EntityReference>(tag, response)

                result.onSuccess {
                    return it
                }
            }
        }

        return null
    }

    protected suspend fun setEntity(tag: String, entityId: Uuid, url: String) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(tag) {
                client.post {
                    url(url)
                    contentType(ContentType.Application.Json)
                    setBody(entityId)
                }
            }?.let { response -> handlePlainHttpResponse(tag, response) }
        }
    }

    suspend fun getNotes(identifier: Uuid): List<Note> {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(GET_NOTES_TAG) {
                client.post {
                    url("$serverUrl/get/notes/$identifier")
                    contentType(ContentType.Application.Json)
                    setBody(identifier)
                }
            }

            if (response != null) {
                val result = handleHttpResponse<List<Note>>(GET_NOTES_TAG, response)

                result.onSuccess {
                    return it
                }
            }
        }

        return emptyList()
    }

    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(REMOVE_TAG) {
            client.delete { url("$serverUrl/remove/$identifier") }
        }

        return handleActionHttpResponse(REMOVE_TAG, response)
    }

    suspend fun removeNote(identifier: Uuid) {
        // TODO: Setup way to check if it was successful or not
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(REMOVE_NOTE_TAG) {
                client.delete { url("$serverUrl/remove/note/$identifier") }
            }

            if (response != null) {
                handlePlainHttpResponse(REMOVE_NOTE_TAG, response)
            }
        }
    }

    suspend fun search(query: String, limit: Int): List<SearchResult> {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(SEARCH_TAG) {
                client.post {
                    url("$serverUrl/search")
                    contentType(ContentType.Application.Json)
                    setBody(SearchQuery(query, limit))
                }
            }

            if (response != null) {
                val result = handleHttpResponse<List<SearchResult>>(SEARCH_TAG, response)

                result.onSuccess {
                    return it
                }
            }
        }

        return emptyList()
    }

    suspend fun update(data: T): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(UPDATE_TAG) {
            client.post {
                url("$serverUrl/update")
                contentType(ContentType.Application.Json)
                setBody(data, itemType)
            }
        }

        return handleActionHttpResponse(UPDATE_TAG, response)
    }

    protected val noServerError: EmptyResult<ServerError>
        get() = Result.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
}
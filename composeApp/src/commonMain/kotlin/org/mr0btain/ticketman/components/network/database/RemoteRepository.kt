package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.ApiQuery
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.NotePaths
import org.mr0btain.ticketman.server.RecordPaths
import org.mr0btain.ticketman.server.fill
import kotlin.uuid.Uuid

/**
 * Type info for an item and a list of them. T is erased inside [RemoteRepository], so kotlinx serialization
 * can't find a serializer for it there; these are made where T is known, see [itemTypes]
 */
class ItemTypes<T>(val item: TypeInfo, val list: TypeInfo)

/** [ItemTypes] for [T], made where [T] is still known */
inline fun <reified T> itemTypes(): ItemTypes<T> = ItemTypes(typeInfo<T>(), typeInfo<List<T>>())

/**
 * Base client for a MainItem type's server routes: the [RecordPaths] and [NotePaths] every record type has, under
 * its [route]. Subclasses pass their route and add calls for their own endpoints.
 *
 * Everything returns a [TmResult], so a screen can tell "nothing there" (an empty list, or a `NOT_FOUND` error for one
 * record) from "couldn't load" (any other error). Every call returns straight away while [serverUrl] is blank.
 * @param tag Logging tag, also the start of each call's own tag
 * @param client The shared, logged in client from RemoteHandler, so tokens and refresh are handled once
 * @param route Where the server mounts this record type, one of the ApiRoutes paths
 * @param types Type info for T, e.g. itemTypes<Customer>()
 */
@Suppress("PropertyName")
abstract class RemoteRepository<T: MainItem>(
    protected val tag: String,
    protected val client: HttpClient,
    val route: String,
    private val types: ItemTypes<T>
) : ServerRepository {
    protected val FETCH_TAG = "$tag:Fetch"
    protected val SEARCH_TAG = "$tag:Search"
    protected val ADD_TAG = "$tag:Add"
    protected val ADD_NOTE_TAG = "$tag:AddNote"
    protected val UPDATE_NOTE_TAG = "$tag:UpdateNote"
    protected val UPDATE_TAG = "$tag:Update"
    protected val REMOVE_TAG = "$tag:Remove"
    protected val REMOVE_NOTE_TAG = "$tag:RemoveNote"

    protected val GET_TAG = "$tag:Get"
    protected val GET_NOTES_TAG = "$tag:GetNotes"

    /**
     * The server's address with [route] on the end. Stays blank until RemoteHandler.setUrl reaches a server, so
     * requests are skipped instead of going to a relative url
     */
    final override var serverUrl: String = ""
        set(value) {
            field = if (value.isBlank()) "" else "${value.trimEnd('/')}$route"
        }

    /** Adds a new record and returns it as saved. Its identifier is made by the client, so it's final before it's sent */
    suspend fun add(data: T): TmResult<T, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(ADD_TAG) {
            client.post {
                url("$serverUrl${RecordPaths.ADD}")
                contentType(ContentType.Application.Json)
                setBody(data, types.item)
            }
        }

        return handleDataHttpResponse(ADD_TAG, response, types.item)
    }

    /** Replaces a note's text and photos. Its photos must have been uploaded first */
    suspend fun updateNote(note: Note): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(UPDATE_NOTE_TAG) {
            client.post {
                url("$serverUrl${NotePaths.UPDATE.fill(note.identifier)}")
                contentType(ContentType.Application.Json)
                setBody(note)
            }
        }

        return handleActionHttpResponse(UPDATE_NOTE_TAG, response)
    }

    /** Adds a note to the record [identifier]. Its photos must have been uploaded first */
    suspend fun addNote(identifier: Uuid, note: Note): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(ADD_NOTE_TAG) {
            client.post {
                url("$serverUrl${NotePaths.ADD.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(note)
            }
        }

        return handleActionHttpResponse(ADD_NOTE_TAG, response)
    }

    /** A page of records in the server's order */
    suspend fun fetch(offset: Long, limit: Int): TmResult<List<T>, ServerError> = fetchPage(FETCH_TAG, RecordPaths.FETCH, offset, limit)

    /** A page from one of the route's paged endpoints, at [path] under [route] */
    protected suspend fun fetchPage(tag: String, path: String, offset: Long, limit: Int): TmResult<List<T>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(tag) {
            client.get {
                url("$serverUrl$path")
                parameter(ApiQuery.OFFSET, offset)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

        return handleDataHttpResponse(tag, response, types.list)
    }

    /** One record. A `NOT_FOUND` error means it isn't there, any other that it couldn't be loaded */
    suspend fun get(identifier: Uuid): TmResult<T, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(GET_TAG) {
            client.get { url("$serverUrl${RecordPaths.GET.fill(identifier)}") }
        }

        return handleDataHttpResponse(GET_TAG, response, types.item)
    }

    /** A linked record's identifier and name, like a ticket's customer, from a GET at [url]. `NOT_FOUND` if there isn't one */
    protected suspend fun getEntity(tag: String, url: String): TmResult<EntityReference, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        return handleDataHttpResponse(tag, networkRequest(tag) { client.get { url(url) } })
    }

    /** Links a record to [entityId] by posting the identifier to [url], like setting a ticket's customer */
    protected suspend fun setEntity(tag: String, entityId: Uuid, url: String): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(tag) {
            client.post {
                url(url)
                contentType(ContentType.Application.Json)
                setBody(entityId)
            }
        }

        return handleActionHttpResponse(tag, response)
    }

    /** The record's notes with their photos */
    suspend fun getNotes(identifier: Uuid): TmResult<List<Note>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(GET_NOTES_TAG) {
            client.get { url("$serverUrl${NotePaths.GET.fill(identifier)}") }
        }

        return handleDataHttpResponse(GET_NOTES_TAG, response)
    }

    /** Deletes the record, and on the server its notes */
    suspend fun remove(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(REMOVE_TAG) {
            client.delete { url("$serverUrl${RecordPaths.REMOVE.fill(identifier)}") }
        }

        return handleActionHttpResponse(REMOVE_TAG, response)
    }

    /** Deletes a note. [identifier] is the note's, the server checks it's on this record type */
    suspend fun removeNote(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(REMOVE_NOTE_TAG) {
            client.delete { url("$serverUrl${NotePaths.REMOVE.fill(identifier)}") }
        }

        return handleActionHttpResponse(REMOVE_NOTE_TAG, response)
    }

    /** Records matching [query], as identifiers and display text */
    suspend fun search(query: String, limit: Int): TmResult<List<SearchResult>, ServerError> {
        if (serverUrl.isBlank()) return noServer()

        val response = networkRequest(SEARCH_TAG) {
            client.get {
                url("$serverUrl${RecordPaths.SEARCH}")
                parameter(ApiQuery.QUERY, query)
                parameter(ApiQuery.LIMIT, limit)
            }
        }

        return handleDataHttpResponse(SEARCH_TAG, response)
    }

    /** Replaces the record's fields. Fields the server owns, like a ticket's number, are ignored */
    suspend fun update(data: T): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(UPDATE_TAG) {
            client.post {
                url("$serverUrl${RecordPaths.UPDATE}")
                contentType(ContentType.Application.Json)
                setBody(data, types.item)
            }
        }

        return handleActionHttpResponse(UPDATE_TAG, response)
    }

    /** What changes return while [serverUrl] is blank */
    protected val noServerError: EmptyResult<ServerError>
        get() = noServer()

    /** What any call returns while [serverUrl] is blank */
    protected fun <R> noServer(): TmResult<R, ServerError> = TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))
}

/**
 * Anything [org.mr0btain.ticketman.components.network.RemoteHandler] points at the server. It keeps them in one
 * list and sets each one's address when the server changes, so a new one can't be left out
 */
interface ServerRepository {
    /** Set to the server's base address, or blank when there isn't one. Each repository adds its own route */
    var serverUrl: String
}

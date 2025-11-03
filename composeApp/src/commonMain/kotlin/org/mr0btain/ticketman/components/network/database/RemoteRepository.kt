package org.mr0btain.ticketman.components.network.database

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.encryption.ClientEncryptionHandler
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Eventually set up a way to designate if the client is Host or remote and adapt this to something more akin to it

@Suppress("PropertyName")
abstract class RemoteRepository(
    tag: String,
    private val client: HttpClient,
    private val encryptionHandler: ClientEncryptionHandler
) {
    protected val SYNC_TAG = "$tag:Sync"
    protected val GET_ALL_TAG = "$tag:GetAll"
    protected val SEND_TAG = "$tag:Send"
    protected val BATCH_SEND_TAG = "$tag:BatchSend"
    protected val UPDATE_TAG = "$tag:Update"
    protected val REMOVE_TAG = "$tag:Remove"

    protected abstract var serverUrl: String

    abstract fun changeUrl(url: String)

    abstract suspend fun sync(data: List<MainItem>): List<MainItem>

    suspend fun send(data: MainItem) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(SEND_TAG) {
                client.post {
                    url("$serverUrl/add")
                    contentType(ContentType.Application.Json)
                    setBody(
                        encryptionHandler.encryptData(
                            Json.encodeToString(data)
                        )
                    )
                }
            }?.let { response -> handleHttpResponse(SEND_TAG, response) }
        }
    }

    suspend fun batchSend(data: List<MainItem>) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(BATCH_SEND_TAG) {
                client.post {
                    url("$serverUrl/add/all")
                    contentType(ContentType.Application.Json)
                    setBody(
                        encryptionHandler.encryptData(
                            Json.encodeToString(data)
                        )
                    )
                }
            }?.let { response -> handleHttpResponse(BATCH_SEND_TAG, response) }
        }
    }

    abstract suspend fun getAll(): List<MainItem>

    suspend fun update(data: MainItem) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(UPDATE_TAG) {
                client.post {
                    url("$serverUrl/update")
                    contentType(ContentType.Application.Json)
                    setBody(
                        encryptionHandler.encryptData(
                            Json.encodeToString(data)
                        )
                    )
                }
            }?.let { response -> handleHttpResponse(SEND_TAG, response) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun remove(identifier: Uuid) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(REMOVE_TAG) {
                client.delete { url("$serverUrl/remove/$identifier") }
            }?.let { response -> handleHttpResponse(REMOVE_TAG, response) }
        }
    }
}
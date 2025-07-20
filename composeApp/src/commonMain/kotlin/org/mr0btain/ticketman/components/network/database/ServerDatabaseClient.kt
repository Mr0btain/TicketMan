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
import org.mr0btain.ticketman.data.MainDataItem

@Suppress("PropertyName")
abstract class ServerDatabaseClient(
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

    abstract suspend fun sync(data: List<MainDataItem>): List<MainDataItem>

    suspend fun send(data: MainDataItem) {
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

    suspend fun batchSend(data: List<MainDataItem>) {
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

    abstract suspend fun getAll(): List<MainDataItem>

    suspend fun update(data: MainDataItem) {
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

    suspend fun remove(identifier: String) {
        if (serverUrl.isNotBlank()) {
            networkErrorTryCatch(REMOVE_TAG) {
                client.delete { url("$serverUrl/remove/$identifier") }
            }?.let { response -> handleHttpResponse(REMOVE_TAG, response) }
        }
    }
}
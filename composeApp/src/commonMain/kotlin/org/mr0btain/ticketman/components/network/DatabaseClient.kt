@file:Suppress("PropertyName")

package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.request.SyncRequest

/**
 * Client for interacting with the server database with a specific type
 */
class DatabaseClient<T : MainDataItem>(
    tag: String,
    val client: HttpClient,
    val serverUrl: String
) {
    val SYNC_TAG = "$tag:Sync"
    val SEND_TAG = "$tag:Send"
    val BATCH_SEND_TAG = "$tag:BatchSend"
    val GET_ALL_TAG = "$tag:GetAll"
    val GET_TAG = "$tag:Get"
    val REMOVE_TAG = "$tag:Remove"

    suspend fun sync(data: List<T>): List<T> {
        val identifiers = data.map { it.identifier }

        println("Sync to: $serverUrl/sync")

        networkErrorTryCatch(SYNC_TAG) {
            client.post {
                url("$serverUrl/sync")
                contentType(ContentType.Application.Json)
                setBody(SyncRequest(identifiers))
            }
        }?.let { response -> handleHttpResponse<List<T>>(SYNC_TAG, response) }
            ?.onSuccess { return it }

        return listOf()
    }

    suspend inline fun <reified T : MainDataItem> send(data: T) {
        networkErrorTryCatch(SEND_TAG) {
            client.post {
                url("$serverUrl/add")
                contentType(ContentType.Application.Json)
                setBody(data)
            }
        }?.let { response -> handleHttpResponse(SEND_TAG, response) }
    }

    suspend fun batchSend(data: List<T>) {
        networkErrorTryCatch(BATCH_SEND_TAG) {
            client.post {
                url("$serverUrl/api/data/customer/add/all")
                contentType(ContentType.Application.Json)
                setBody(data)
            }
        }?.let { response -> handleHttpResponse(BATCH_SEND_TAG, response) }
    }

    suspend inline fun <reified T : MainDataItem> getAll(): List<T> {
        networkErrorTryCatch(GET_ALL_TAG) {
            client.get { url("${serverUrl}/all") }
        }?.let { response -> handleHttpResponse<List<T>>(GET_ALL_TAG, response) }
            ?.onSuccess { return it }

        return emptyList()
    }

    suspend inline fun <reified T : MainDataItem> get(identifier: Int): MainDataItem? {
        networkErrorTryCatch(GET_TAG) {
            client.post {
                url(serverUrl)
                contentType(ContentType.Application.Json)
                setBody(identifier)
            }
        }?.let { response -> handleHttpResponse<T>(GET_TAG,response) }
            ?.onSuccess { return it }

        return null
    }

    suspend fun remove(identifier: String) {
        networkErrorTryCatch(REMOVE_TAG) {
            client.delete { url("$serverUrl/remove/$identifier") }
        }?.let { response -> handleHttpResponse(REMOVE_TAG, response) }
    }
}
package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.network.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.SyncRequest

/**
 * Client for interacting with the server database with a specific type
 */
class DatabaseClient<T : MainDataItem>(
    val TAG: String,
    val client: HttpClient,
    val serverUrl: String
) {
    suspend fun sync(data: List<T>): List<T> {
        val identifiers = data.map { it.identifier }

        val response = try {
            // Send request and receive response
            client.post {
                url("$serverUrl/sync")
                contentType(ContentType.Application.Json)
                setBody(SyncRequest(identifiers))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Sync", NetworkError.NO_INTERNET.name)
            return emptyList()
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Sync", NetworkError.SERIALIZATION.name)
            return emptyList()
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Sync", SimpleError.FAIL)
            return emptyList()
        }

        handleHttpResponse<List<T>>(TAG, response)
            .onSuccess { data ->
                AppLogger.m("$TAG:Sync", SimpleError.SUCCESS)
                return data
            }

        return listOf()
    }

    suspend inline fun <reified T : MainDataItem> send(data: T) {
        val response = try {
            client.post {
                url("$serverUrl/add")
                contentType(ContentType.Application.Json)
                setBody(data)
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.e("${ServerDatabaseClient.Companion.TAG}:Send", ex.message.toString(), ex)
            return
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.e("${ServerDatabaseClient.Companion.TAG}:Send", ex.message.toString(), ex)
            return
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.e("${ServerDatabaseClient.Companion.TAG}:Send", ex.message.toString(), ex)
            return
        }


        handleHttpResponse<T>(TAG, response)
            .onSuccess {
                AppLogger.m("$TAG:Send", SimpleError.SUCCESS)
            }
    }

    suspend fun batchSend(data: List<T>) {
        val response = try {
            client.post {
                url("$serverUrl/api/data/customer/add/all")
                contentType(ContentType.Application.Json)
                setBody(data)
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:BatchSend", NetworkError.NO_INTERNET.name)
            return
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:BatchSend", NetworkError.SERIALIZATION.name)
            return
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i("$TAG:BatchSend", SimpleError.FAIL)
            return
        }

        handleHttpResponse(TAG, response)
    }

    suspend fun getAll(): List<T> {
        val response = try {
            client.get { url("${serverUrl}/all") }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:GetAll", NetworkError.NO_INTERNET.name)
            return emptyList()
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:GetAll", NetworkError.SERIALIZATION.name)
            return emptyList()
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i("$TAG:GetAll", SimpleError.FAIL.name)
            return emptyList()
        }

        handleHttpResponse<List<T>>(TAG, response)
            .onSuccess { value ->
                AppLogger.m("$TAG:GetAll", SimpleError.SUCCESS.name)
                return value
            }

        return emptyList()
    }

    suspend inline fun <reified T : MainDataItem> get(identifier: Int): MainDataItem? {
        val response = try {
            client.post {
                url(serverUrl)
                contentType(ContentType.Application.Json)
                setBody(identifier)
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Get", NetworkError.NO_INTERNET.name)
            return null
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Get", NetworkError.SERIALIZATION.name)
            return null
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Get", SimpleError.FAIL)
            return null
        }

        handleHttpResponse<T>(TAG, response)
            .onSuccess { value ->
                AppLogger.m("$TAG:Get", SimpleError.SUCCESS)
                return value
            }

        return null
    }

    suspend fun remove(identifier: Int) {
        val response = try {
            client.delete { url("$serverUrl/remove/$identifier") }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Get", NetworkError.NO_INTERNET.name)
            return
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Get", NetworkError.SERIALIZATION.name)
            return
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i("$TAG:Get", SimpleError.FAIL)
            return
        }

        handleHttpResponse(TAG, response)
    }
}

suspend inline fun <reified T> handleHttpResponse(tag: String, response: HttpResponse): Result<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            val response = response.body<T>()
            AppLogger.m(tag, SimpleError.SUCCESS)
            Result.Success(response)
        }

        401 -> {
            AppLogger.m(tag, NetworkError.UNAUTHORIZED.name)
            Result.Error(NetworkError.UNAUTHORIZED)
        }

        409 -> {
            AppLogger.m(tag, NetworkError.CONFLICT.name)
            Result.Error(NetworkError.CONFLICT)
        }

        408 -> {
            AppLogger.m(tag, NetworkError.REQUEST_TIMEOUT.name)
            Result.Error(NetworkError.REQUEST_TIMEOUT)
        }

        413 -> {
            AppLogger.m(tag, NetworkError.PAYLOAD_TOO_LARGE.name)
            Result.Error(NetworkError.PAYLOAD_TOO_LARGE)
        }

        in 500..599 -> {
            AppLogger.m(tag, NetworkError.SERVER_ERROR.name)
            Result.Error(NetworkError.SERVER_ERROR)
        }

        else -> {
            AppLogger.m(tag, NetworkError.UNKNOWN.name)
            Result.Error(NetworkError.UNKNOWN)
        }
    }
}

fun handleHttpResponse(tag: String, response: HttpResponse) {
    when (response.status.value) {
        in 200..299 -> AppLogger.m(tag, SimpleError.SUCCESS)
        401 -> AppLogger.m(tag, NetworkError.UNAUTHORIZED.name)
        409 -> AppLogger.m(tag, NetworkError.CONFLICT.name)
        408 -> AppLogger.m(tag, NetworkError.REQUEST_TIMEOUT.name)
        413 -> AppLogger.m(tag, NetworkError.PAYLOAD_TOO_LARGE.name)
        in 500..599 -> AppLogger.m(tag, NetworkError.SERVER_ERROR.name)
        else -> AppLogger.m(tag, NetworkError.UNKNOWN.name)
    }
}
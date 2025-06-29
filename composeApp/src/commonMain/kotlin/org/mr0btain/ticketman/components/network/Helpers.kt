package org.mr0btain.ticketman.components.network

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.server.NetworkError

inline fun <T> networkErrorTryCatch(tag: String, block: () -> T): T? {
    return try {
        block()
    } catch (ex: UnresolvedAddressException) {
        ex.printStackTrace()
        AppLogger.i(tag, NetworkError.NO_INTERNET.name)
        null
    } catch (ex: SerializationException) {
        ex.printStackTrace()
        AppLogger.i(tag, NetworkError.SERIALIZATION.name)
        null
    } catch (ex: Exception) {
        ex.printStackTrace()
        AppLogger.i(tag, SimpleError.FAIL)
        null
    }
}

suspend inline fun <reified T> handleHttpResponse(tag: String, response: HttpResponse): org.mr0btain.ticketman.error.Result<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            val response = response.body<T>()
            AppLogger.m(tag, SimpleError.SUCCESS)
            org.mr0btain.ticketman.error.Result.Success(response)
        }

        401 -> {
            AppLogger.i(tag, NetworkError.UNAUTHORIZED.name)
            org.mr0btain.ticketman.error.Result.Error(NetworkError.UNAUTHORIZED)
        }

        409 -> {
            AppLogger.i(tag, NetworkError.CONFLICT.name)
            org.mr0btain.ticketman.error.Result.Error(NetworkError.CONFLICT)
        }

        408 -> {
            AppLogger.i(tag, NetworkError.REQUEST_TIMEOUT.name)
            org.mr0btain.ticketman.error.Result.Error(NetworkError.REQUEST_TIMEOUT)
        }

        413 -> {
            AppLogger.i(tag, NetworkError.PAYLOAD_TOO_LARGE.name)
            org.mr0btain.ticketman.error.Result.Error(NetworkError.PAYLOAD_TOO_LARGE)
        }

        in 500..599 -> {
            AppLogger.i(tag, NetworkError.SERVER_ERROR.name)
            org.mr0btain.ticketman.error.Result.Error(NetworkError.SERVER_ERROR)
        }

        else -> {
            AppLogger.i(tag, NetworkError.UNKNOWN.name)
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
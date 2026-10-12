package org.mr0btain.ticketman.components.network

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.util.network.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.NetworkError

inline fun <reified T> safeCastList(value: Any?): List<T>? {
    val list = value as? List<*> ?: return null
    return if (list.all { it is T }) list.filterIsInstance<T>() else null
}

inline fun <reified T> safeCast(value: Any?): T? {
    return value as? T
}

inline fun <T> networkErrorTryCatch(tag: String, block: () -> T): T? {
    return try {
        block()
    } catch (ex: UnresolvedAddressException) {
        AppLogger.e(tag, NetworkError.NO_INTERNET.name, ex)
        null
    } catch (ex: SerializationException) {
        AppLogger.e(tag, NetworkError.SERIALIZATION.name, ex)
        null
    } catch (ex: Exception) {
        AppLogger.e(tag, SimpleResult.FAIL.message, ex)
        null
    }
}

/**
 * Handles the http response form server logging the result whether it be a fail or success
 */
suspend inline fun <reified T> handleHttpResponse(tag: String, response: HttpResponse): Result<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            try {
                val result = Result.Success(response.body<T>())
                AppLogger.m(tag, SimpleResult.SUCCESS)
                result
            } catch (ex: Exception) {
                AppLogger.i(tag, ex.toString())
                Result.Error(NetworkError.UNKNOWN)
            }
        }

        401 -> {
            AppLogger.i(tag, NetworkError.UNAUTHORIZED.name)
            Result.Error(NetworkError.UNAUTHORIZED)
        }

        409 -> {
            AppLogger.i(tag, NetworkError.CONFLICT.name)
            Result.Error(NetworkError.CONFLICT)
        }

        408 -> {
            AppLogger.i(tag, NetworkError.REQUEST_TIMEOUT.name)
            Result.Error(NetworkError.REQUEST_TIMEOUT)
        }

        413 -> {
            AppLogger.i(tag, NetworkError.PAYLOAD_TOO_LARGE.name)
            Result.Error(NetworkError.PAYLOAD_TOO_LARGE)
        }

        in 500..599 -> {
            AppLogger.i(tag, NetworkError.SERVER_ERROR.name)
            Result.Error(NetworkError.SERVER_ERROR)
        }

        else -> {
            AppLogger.i(tag, NetworkError.UNKNOWN.name)
            Result.Error(NetworkError.UNKNOWN)
        }
    }
}

fun handlePlainHttpResponse(tag: String, response: HttpResponse) {
    when (response.status.value) {
        in 200..299 -> AppLogger.m(tag, SimpleResult.SUCCESS)
        401 -> AppLogger.i(tag, NetworkError.UNAUTHORIZED.name)
        409 -> AppLogger.i(tag, NetworkError.CONFLICT.name)
        408 -> AppLogger.i(tag, NetworkError.REQUEST_TIMEOUT.name)
        413 -> AppLogger.i(tag, NetworkError.PAYLOAD_TOO_LARGE.name)
        in 500..599 -> AppLogger.i(tag, NetworkError.SERVER_ERROR.name)
        else -> AppLogger.i(tag, NetworkError.UNKNOWN.name)
    }
}
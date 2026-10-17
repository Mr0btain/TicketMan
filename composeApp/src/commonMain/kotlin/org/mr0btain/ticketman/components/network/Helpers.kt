package org.mr0btain.ticketman.components.network

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.util.network.*
import io.ktor.util.reflect.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.error.EmptyResult
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
suspend inline fun <reified T> handleHttpResponse(tag: String, response: HttpResponse): Result<T, NetworkError> =
    handleHttpResponse(tag, response, typeInfo<T>())

/**
 * Same as above but with the body type supplied explicitly. Needed from generic classes where T is erased,
 * since kotlinx serialization can't find a serializer for an erased type.
 */
suspend fun <T> handleHttpResponse(tag: String, response: HttpResponse, bodyType: TypeInfo): Result<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            try {
                val result = Result.Success(response.body<T>(bodyType))
                AppLogger.m(tag, SimpleResult.SUCCESS)
                result
            } catch (ex: Exception) {
                AppLogger.i(tag, ex.toString())
                Result.Error(NetworkError.UNKNOWN)
            }
        }

        else -> {
            val error = networkErrorFor(response.status.value)
            AppLogger.i(tag, error.name)
            Result.Error(error)
        }
    }
}

/** Maps a non success status code to a [NetworkError] */
fun networkErrorFor(statusCode: Int): NetworkError =
    when (statusCode) {
        400 -> NetworkError.BAD_REQUEST
        401 -> NetworkError.UNAUTHORIZED
        403 -> NetworkError.FORBIDDEN
        404 -> NetworkError.NOT_FOUND
        408 -> NetworkError.REQUEST_TIMEOUT
        409 -> NetworkError.CONFLICT
        413 -> NetworkError.PAYLOAD_TOO_LARGE
        in 500..599 -> NetworkError.SERVER_ERROR
        else -> NetworkError.UNKNOWN
    }

fun handlePlainHttpResponse(tag: String, response: HttpResponse) {
    if (response.status.value in 200..299) {
        AppLogger.m(tag, SimpleResult.SUCCESS)
    } else {
        AppLogger.i(tag, networkErrorFor(response.status.value).name)
    }
}

/**
 * For calls where we only care whether it worked. On failure the server's plain text reason, if it sent one,
 * comes back in the [ServerError] so it can be shown to the user
 */
suspend fun handleActionHttpResponse(tag: String, response: HttpResponse?): EmptyResult<ServerError> {
    if (response == null) return Result.Error(ServerError(NetworkError.NO_INTERNET))

    if (response.status.value in 200..299) {
        AppLogger.m(tag, SimpleResult.SUCCESS)
        return Result.Success(Unit)
    }

    val error = networkErrorFor(response.status.value)

    // Only take short plain text bodies, anything else is a page or json we don't want in a snackbar
    val reason = try {
        response.bodyAsText().takeIf { it.isNotBlank() && it.length < 200 && !it.trimStart().startsWith("{") }
    } catch (ex: Exception) {
        null
    }

    AppLogger.i(tag, "${error.name}${reason?.let { ": $it" } ?: ""}")
    return Result.Error(ServerError(error, reason))
}

/**
 * Like [handleActionHttpResponse] but also reads the body on success
 */
suspend inline fun <reified T> handleDataHttpResponse(tag: String, response: HttpResponse?): Result<T, ServerError> {
    if (response != null && response.status.value in 200..299) {
        return when (val result = handleHttpResponse<T>(tag, response)) {
            is Result.Success -> result
            is Result.Error -> Result.Error(ServerError(result.error))
        }
    }

    return when (val failed = handleActionHttpResponse(tag, response)) {
        is Result.Error -> failed
        is Result.Success -> Result.Error(ServerError(NetworkError.UNKNOWN))
    }
}

package org.mr0btain.ticketman.components.network

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.util.network.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
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

/**
 * Runs a network call, turning anything it throws into the [NetworkError] that says what went wrong, so a
 * timeout or a response the app can't read isn't reported as the server being down. Cancellation is let through
 */
inline fun <T> networkRequest(tag: String, block: () -> T): Result<T, NetworkError> {
    return try {
        Result.Success(block())
    } catch (ex: Exception) {
        val error = networkErrorFor(ex) ?: throw ex
        AppLogger.e(tag, error.name, ex)
        Result.Error(error)
    }
}

/** Like [networkRequest] for calls that only need the value, null on failure */
inline fun <T> networkErrorTryCatch(tag: String, block: () -> T): T? =
    (networkRequest(tag, block) as? Result.Success)?.data

/** Maps what a network call threw to a [NetworkError], null for cancellation which has to be rethrown */
fun networkErrorFor(ex: Throwable): NetworkError? =
    when (ex) {
        // Timeouts first, Ktor's are IOExceptions too
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> NetworkError.REQUEST_TIMEOUT
        is CancellationException -> null
        is ResponseException -> networkErrorFor(ex.response.status.value)
        is SerializationException, is ContentConvertException, is NoTransformationFoundException -> NetworkError.SERIALIZATION
        is UnresolvedAddressException, is IOException -> NetworkError.NO_INTERNET
        else -> NetworkError.UNKNOWN
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
                AppLogger.e(tag, NetworkError.SERIALIZATION.name, ex)
                Result.Error(networkErrorFor(ex) ?: throw ex)
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
        429 -> NetworkError.TOO_MANY_REQUESTS
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
suspend fun handleActionHttpResponse(tag: String, request: Result<HttpResponse, NetworkError>): EmptyResult<ServerError> =
    when (request) {
        is Result.Error -> Result.Error(ServerError(request.error))
        is Result.Success -> handleActionHttpResponse(tag, request.data)
    }

/** [handleActionHttpResponse] for a response that arrived */
suspend fun handleActionHttpResponse(tag: String, response: HttpResponse): EmptyResult<ServerError> {

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
suspend inline fun <reified T> handleDataHttpResponse(tag: String, request: Result<HttpResponse, NetworkError>): Result<T, ServerError> {
    val response = when (request) {
        is Result.Error -> return Result.Error(ServerError(request.error))
        is Result.Success -> request.data
    }

    if (response.status.value in 200..299) {
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

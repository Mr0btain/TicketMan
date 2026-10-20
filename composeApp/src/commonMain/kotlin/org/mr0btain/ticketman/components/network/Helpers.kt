package org.mr0btain.ticketman.components.network

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.network.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
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
inline fun <T> networkRequest(tag: String, block: () -> T): TmResult<T, NetworkError> {
    return try {
        TmResult.Success(block())
    } catch (ex: Exception) {
        val error = networkErrorFor(ex) ?: throw ex
        AppLogger.e(tag, error.name, ex)
        TmResult.Error(error)
    }
}

/** Like [networkRequest] for calls that only need the value, null on failure */
inline fun <T> networkErrorTryCatch(tag: String, block: () -> T): T? =
    (networkRequest(tag, block) as? TmResult.Success)?.data

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
suspend inline fun <reified T> handleHttpResponse(tag: String, response: HttpResponse): TmResult<T, NetworkError> =
    handleHttpResponse(tag, response, typeInfo<T>())

/**
 * Same as above but with the body type supplied explicitly. Needed from generic classes where T is erased,
 * since kotlinx serialization can't find a serializer for an erased type.
 */
suspend fun <T> handleHttpResponse(tag: String, response: HttpResponse, bodyType: TypeInfo): TmResult<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            try {
                val result = TmResult.Success(response.body<T>(bodyType))
                AppLogger.m(tag, SimpleResult.SUCCESS)
                result
            } catch (ex: Exception) {
                AppLogger.e(tag, NetworkError.SERIALIZATION.name, ex)
                TmResult.Error(networkErrorFor(ex) ?: throw ex)
            }
        }

        else -> {
            val error = networkErrorFor(response.status.value)
            AppLogger.i(tag, error.name)
            TmResult.Error(error)
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
        // The server never sends these itself, only a proxy in front of it does
        502, 503, 504 -> NetworkError.PROXY_SERVER_DOWN
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
suspend fun handleActionHttpResponse(tag: String, request: TmResult<HttpResponse, NetworkError>): EmptyResult<ServerError> =
    when (request) {
        is TmResult.Error -> TmResult.Error(ServerError(request.error))
        is TmResult.Success -> handleActionHttpResponse(tag, request.data)
    }

/** [handleActionHttpResponse] for a response that arrived */
suspend fun handleActionHttpResponse(tag: String, response: HttpResponse): EmptyResult<ServerError> {

    if (response.status.value in 200..299) {
        AppLogger.m(tag, SimpleResult.SUCCESS)
        return TmResult.Success(Unit)
    }

    val body = try {
        response.bodyAsText()
    } catch (ex: Exception) {
        ""
    }

    // Only take short plain text bodies, anything else is a page or json we don't want in a snackbar
    val reason = body.takeIf { it.isNotBlank() && it.length < 200 && !isPageOrJson(response, it) }

    // The server says why it turns a body down for its size; a proxy sends a page or nothing
    val error = when {
        response.status == HttpStatusCode.PayloadTooLarge && reason == null -> NetworkError.PROXY_TOO_LARGE
        else -> networkErrorFor(response.status.value)
    }

    AppLogger.i(tag, "${error.name}${reason?.let { ": $it" } ?: ""}")
    return TmResult.Error(ServerError(error, reason))
}

/** An HTML page (a proxy's error page) or JSON, neither of which is a reason to show someone */
private fun isPageOrJson(response: HttpResponse, body: String): Boolean {
    val start = body.trimStart()
    return response.contentType()?.match(ContentType.Text.Html) == true || start.startsWith("<") || start.startsWith("{")
}

/**
 * Like [handleActionHttpResponse] but also reads the body on success
 */
suspend inline fun <reified T> handleDataHttpResponse(tag: String, request: TmResult<HttpResponse, NetworkError>): TmResult<T, ServerError> {
    val response = when (request) {
        is TmResult.Error -> return TmResult.Error(ServerError(request.error))
        is TmResult.Success -> request.data
    }

    if (response.status.value in 200..299) {
        return when (val result = handleHttpResponse<T>(tag, response)) {
            is TmResult.Success -> result
            is TmResult.Error -> TmResult.Error(ServerError(result.error))
        }
    }

    return when (val failed = handleActionHttpResponse(tag, response)) {
        is TmResult.Error -> failed
        is TmResult.Success -> TmResult.Error(ServerError(NetworkError.UNKNOWN))
    }
}

/** [handleDataHttpResponse] for a body whose type isn't known at compile time, like a generic repository's T */
suspend fun <T> handleDataHttpResponse(tag: String, request: TmResult<HttpResponse, NetworkError>, bodyType: TypeInfo): TmResult<T, ServerError> {
    val response = when (request) {
        is TmResult.Error -> return TmResult.Error(ServerError(request.error))
        is TmResult.Success -> request.data
    }

    if (response.status.value in 200..299) {
        return when (val result = handleHttpResponse<T>(tag, response, bodyType)) {
            is TmResult.Success -> result
            is TmResult.Error -> TmResult.Error(ServerError(result.error))
        }
    }

    return when (val failed = handleActionHttpResponse(tag, response)) {
        is TmResult.Error -> failed
        is TmResult.Success -> TmResult.Error(ServerError(NetworkError.UNKNOWN))
    }
}

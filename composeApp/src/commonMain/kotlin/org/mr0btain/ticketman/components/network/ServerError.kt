package org.mr0btain.ticketman.components.network

import org.mr0btain.ticketman.error.Error
import org.mr0btain.ticketman.server.NetworkError

/**
 * A failed request along with the reason the server gave, if any
 */
data class ServerError(
    val type: NetworkError,
    val reason: String? = null
) : Error {
    /** Something readable to show the user */
    val message: String
        get() = reason ?: when (type) {
            NetworkError.NO_INTERNET -> "Couldn't reach the server"
            NetworkError.UNAUTHORIZED -> "Not allowed, try logging in again"
            NetworkError.FORBIDDEN -> "The server doesn't allow that"
            NetworkError.NOT_FOUND -> "Not found on the server"
            NetworkError.CONFLICT -> "That conflicts with existing data"
            NetworkError.BAD_REQUEST -> "The server rejected the request"
            NetworkError.REQUEST_TIMEOUT -> "The request timed out"
            NetworkError.SERVER_ERROR -> "The server had an error"
            else -> "Something went wrong"
        }
}

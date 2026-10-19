package org.mr0btain.ticketman.components.network

import org.mr0btain.ticketman.error.Error
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.NetworkError

/**
 * A failed request along with the reason the server gave, if any
 */
data class ServerError(
    val type: NetworkError,
    val reason: String? = null
) : Error {
    /** Something readable to show the user */
    override val message: String
        get() = reason ?: type.message
}

/** Whether the server said there's no such thing, as opposed to failing to answer */
val ServerError.isNotFound: Boolean
    get() = type == NetworkError.NOT_FOUND

/** For a linked record that may not be there, like a ticket's technician: not found becomes null, other failures stay */
fun <T> Result<T, ServerError>.orNullIfNotFound(): Result<T?, ServerError> =
    if (this is Result.Error && error.isNotFound) Result.Success(null) else this

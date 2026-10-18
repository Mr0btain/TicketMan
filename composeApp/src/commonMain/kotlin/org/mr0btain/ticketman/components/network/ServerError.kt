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
    override val message: String
        get() = reason ?: type.message
}

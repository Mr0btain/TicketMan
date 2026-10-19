package org.mr0btain.ticketman.server

import org.mr0btain.ticketman.error.Error

enum class NetworkError(override val message: String) : Error {
    REQUEST_TIMEOUT("The request timed out"),
    BAD_REQUEST("The server rejected the request"),
    UNAUTHORIZED("Not allowed, try logging in again"),
    FORBIDDEN("You don't have access to that"),
    NOT_FOUND("Not found on the server"),
    CONFLICT("That conflicts with existing data"),
    TOO_MANY_REQUESTS("Too many tries, wait a moment and try again"),
    NO_INTERNET("Couldn't reach the server"),
    PAYLOAD_TOO_LARGE("That's too big to send"),
    SERVER_ERROR("The server had an error"),
    SERIALIZATION("The server sent something the app couldn't read"),
    UNKNOWN("Something went wrong");
}

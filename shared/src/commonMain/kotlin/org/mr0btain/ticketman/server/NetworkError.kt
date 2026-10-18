package org.mr0btain.ticketman.server

import org.mr0btain.ticketman.error.Error

enum class NetworkError(override val message: String) : Error {
    REQUEST_TIMEOUT("The request timed out"),
    BAD_REQUEST("The server rejected the request"),
    UNAUTHORIZED("Not allowed, try logging in again"),
    FORBIDDEN("The server doesn't allow that"),
    NOT_FOUND("Not found on the server"),
    CONFLICT("That conflicts with existing data"),
    TOO_MANY_REQUESTS("Something went wrong"),
    NO_INTERNET("Couldn't reach the server"),
    PAYLOAD_TOO_LARGE("Something went wrong"),
    SERVER_ERROR("The server had an error"),
    SERIALIZATION("Something went wrong"),
    UNKNOWN("Something went wrong");
}

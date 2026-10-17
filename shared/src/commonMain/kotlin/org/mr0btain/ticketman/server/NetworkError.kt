package org.mr0btain.ticketman.server

import org.mr0btain.ticketman.error.Error

enum class NetworkError : Error {
    REQUEST_TIMEOUT,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    TOO_MANY_REQUESTS,
    NO_INTERNET,
    PAYLOAD_TOO_LARGE,
    SERVER_ERROR,
    SERIALIZATION,
    UNKNOWN;
}
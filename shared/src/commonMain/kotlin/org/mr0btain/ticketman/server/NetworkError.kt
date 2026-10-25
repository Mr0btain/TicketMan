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

    /** The server turned this app away for being a major version away from it (426) */
    INCOMPATIBLE_VERSION("This app and the server are a major version apart. Update the older one"),
    NO_INTERNET("Couldn't reach the server"),
    PAYLOAD_TOO_LARGE("That's too big to send"),

    /** A reverse proxy in front of the server turned the request down for its size, before the server saw it */
    PROXY_TOO_LARGE("Bigger than the proxy allows. Ask your admin to raise the proxy's upload limit"),

    /** A reverse proxy answered (502, 503, 504) but couldn't get through to the server behind it */
    PROXY_SERVER_DOWN("The proxy is up but the TicketMan server isn't answering"),
    SERVER_ERROR("The server had an error"),
    SERIALIZATION("The server sent something the app couldn't read"),
    UNKNOWN("Something went wrong");
}

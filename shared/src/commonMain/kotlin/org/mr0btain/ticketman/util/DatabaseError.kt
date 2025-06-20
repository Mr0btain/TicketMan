package org.mr0btain.ticketman.util

enum class DatabaseError : Error {
    UNAUTHORIZED,
    CONFLICT,
    TOO_MANY_REQUESTS,
    UNKNOWN;
}
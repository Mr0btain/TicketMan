package org.mr0btain.ticketman.database

import org.mr0btain.ticketman.error.Error

enum class DatabaseError : Error {
    UNAUTHORIZED,
    CONFLICT,
    TOO_MANY_REQUESTS,
    UNKNOWN;
}
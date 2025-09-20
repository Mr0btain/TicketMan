package org.mr0btain.ticketman.data.uuid

import java.util.UUID

fun uuid7ToMillis(uuid: UUID): Long {
    val msb = uuid.mostSignificantBits

    // Extract the first 48 bits (timestamp in ms)
    return (msb ushr 16) and 0x0000FFFFFFFFFFFFL
}

/**
 * If this UUID was generated with UUIDv7 then we can extract the time from it.
 */

fun UUID.v7toMillis(): Long {
    return (mostSignificantBits ushr 16) and 0x0000FFFFFFFFFFFFL
}

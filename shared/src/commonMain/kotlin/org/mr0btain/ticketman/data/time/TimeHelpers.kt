package org.mr0btain.ticketman.data.time

import kotlin.time.Instant
import kotlin.uuid.Uuid

// Helper functions for dealing with time

fun Uuid.createdAt(): Instant {
    val bytes = toByteArray()

    var timestamp = 0L
    for (i in 0 until 6) {
        timestamp = (timestamp shl 8) or (bytes[i].toLong() and 0xFF)
    }

    return Instant.fromEpochMilliseconds(timestamp)
}

fun Uuid.timestampMillis(): Long {
    val bytes = toByteArray()

    return (bytes[0].toLong() and 0xFF shl 40) or
            (bytes[1].toLong() and 0xFF shl 32) or
            (bytes[2].toLong() and 0xFF shl 24) or
            (bytes[3].toLong() and 0xFF shl 16) or
            (bytes[4].toLong() and 0xFF shl 8) or
            (bytes[5].toLong() and 0xFF)
}
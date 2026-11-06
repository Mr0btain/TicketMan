package org.mr0btain.ticketman.logging

/**
 * Lets one thread at a time into the log, so lines from several threads don't interleave in the file. A plain
 * lock: [withLock] mustn't call back into [AppLogger]
 */
internal expect class LogLock() {
    fun <T> withLock(block: () -> T): T
}

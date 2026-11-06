package org.mr0btain.ticketman.logging

// A browser page runs on one thread
internal actual class LogLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = block()
}

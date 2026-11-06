package org.mr0btain.ticketman.logging

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual class LogLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

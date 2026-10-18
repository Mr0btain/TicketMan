package org.mr0btain.ticketman.components.service

import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Counts attempts per key (like an address) and refuses once there are too many in the window.
 * Kept in memory, so it starts fresh when the server restarts
 * @param now Epoch millis, swapped out in tests
 */
class RequestLimiter(
    private val maxAttempts: Int,
    private val window: Duration,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val attempts = mutableMapOf<String, MutableList<Long>>()

    /** Records an attempt, false if this key has already used up the window */
    @Synchronized
    fun tryAcquire(key: String): Boolean {
        val now = now()
        val since = now - window.inWholeMilliseconds
        val recent = attempts.getOrPut(key) { mutableListOf() }

        recent.removeAll { it < since }
        if (recent.size >= maxAttempts) return false

        recent.add(now)
        return true
    }
}

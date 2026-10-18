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
        if (isLimited(key)) return false

        record(key)
        return true
    }

    /** Whether this key has used up the window, without counting this as an attempt */
    @Synchronized
    fun isLimited(key: String): Boolean {
        val recent = attempts[key] ?: return false

        recent.removeAll { it < since() }
        if (recent.isEmpty()) attempts.remove(key)

        return recent.size >= maxAttempts
    }

    /** Counts an attempt for this key, for callers that only count some (like failed logins) */
    @Synchronized
    fun record(key: String) {
        // Keys that only show up once (a made up username) would otherwise stay forever
        if (attempts.size >= PRUNE_AT) prune()

        attempts.getOrPut(key) { mutableListOf() }.add(now())
    }

    private fun prune() {
        val since = since()

        attempts.values.forEach { recent -> recent.removeAll { it < since } }
        attempts.values.removeAll { it.isEmpty() }
    }

    private fun since(): Long = now() - window.inWholeMilliseconds

    private companion object {
        const val PRUNE_AT = 1000
    }
}

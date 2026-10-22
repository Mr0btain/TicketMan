package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.server.response.LoginLimitsResponse
import org.mr0btain.ticketman.server.response.LoginLock
import org.mr0btain.ticketman.server.response.LoginLockKind
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Caps wrong password logins. Only failures count, so staff logging in all day never hit it. Three limits, all over
 * the last [WINDOW]:
 * - Per address, the device (from the proxy's `X-Forwarded-For`): [ADDRESS_MAX] wrong tries
 * - Per username: [FREE_TRIES] wrong tries, then each one after locks the name for twice as long as the last, from
 *   [FIRST_WAIT] up to [LONGEST_WAIT]. A locked name is refused even with the right password. The count starts over
 *   after a [WINDOW] without a wrong try, or a good login
 * - Server wide: past [SURGE_FAILURES] wrong tries from everywhere, guessing spread over many addresses, an address
 *   that has had a wrong try in the window waits until it ages out, so each gets one try
 *
 * Kept in memory, a restart clears it
 * @param onLock Told when a username or address is newly locked, and when a surge starts, for the audit log
 */
class LoginLimiter(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onLock: (LoginLock) -> Unit = {},
    private val onSurge: (failures: Int) -> Unit = {}
) {
    private class NameState(var failures: Int = 0, var lastFailureAt: Long = 0, var lockedUntil: Long = 0)

    // Times of wrong tries in the window, oldest first
    private val addresses = mutableMapOf<String, ArrayDeque<Long>>()
    private val everywhere = ArrayDeque<Long>()
    private val usernames = mutableMapOf<String, NameState>()

    /** Millis until this address and username may try again, null when they may now */
    @Synchronized
    fun waitFor(address: String, username: String): Long? {
        val time = now()
        prune(time)

        val waits = listOfNotNull(
            usernames[username.key()]?.lockedUntil?.takeIf { it > time }?.minus(time),
            addressLockedUntil(address)?.minus(time),
            if (isSurge()) addresses[address]?.lastOrNull()?.plus(WINDOW_MILLIS)?.minus(time) else null
        )

        return waits.filter { it > 0 }.maxOrNull()
    }

    /** Counts a wrong password */
    @Synchronized
    fun failed(address: String, username: String) {
        val time = now()
        prune(time)

        val wasSurge = isSurge()
        everywhere.addLast(time)
        if (!wasSurge && isSurge()) onSurge(everywhere.size)

        val tries = addresses.getOrPut(address) { ArrayDeque() }.apply { addLast(time) }
        if (tries.size == ADDRESS_MAX) {
            onLock(LoginLock(LoginLockKind.ADDRESS, address, tries.size, addressLockedUntil(address) ?: time))
        }

        val key = username.key()
        val name = usernames.getOrPut(key) { NameState() }
        name.failures++
        name.lastFailureAt = time

        if (name.failures > FREE_TRIES) {
            name.lockedUntil = time + waitAfter(name.failures).inWholeMilliseconds
            onLock(LoginLock(LoginLockKind.USERNAME, key, name.failures, name.lockedUntil))
        }
    }

    /** A good login starts the username's count over */
    @Synchronized
    fun succeeded(username: String) {
        usernames.remove(username.key())
    }

    /** What's locked right now, for the admin screen */
    @Synchronized
    fun snapshot(): LoginLimitsResponse {
        val time = now()
        prune(time)

        val names = usernames.filterValues { it.lockedUntil > time }
            .map { (key, state) -> LoginLock(LoginLockKind.USERNAME, key, state.failures, state.lockedUntil) }
        val devices = addresses.keys.mapNotNull { address ->
            addressLockedUntil(address)?.let { LoginLock(LoginLockKind.ADDRESS, address, addresses.getValue(address).size, it) }
        }

        return LoginLimitsResponse(
            locks = (names + devices).sortedByDescending { it.lockedUntil },
            recentFailures = everywhere.size,
            surge = isSurge(),
            windowMinutes = WINDOW.inWholeMinutes.toInt()
        )
    }

    /** Lets a username or address try again straight away. False when it had nothing counted */
    @Synchronized
    fun unlock(kind: LoginLockKind, key: String): Boolean = when (kind) {
        LoginLockKind.USERNAME -> usernames.remove(key.key()) != null
        LoginLockKind.ADDRESS -> addresses.remove(key.trim()) != null
    }

    private fun isSurge() = everywhere.size >= SURGE_FAILURES

    // When enough of the address's tries age out to bring it under the cap, null when it's under already
    private fun addressLockedUntil(address: String): Long? {
        val tries = addresses[address] ?: return null
        if (tries.size < ADDRESS_MAX) return null
        return tries[tries.size - ADDRESS_MAX] + WINDOW_MILLIS
    }

    private fun prune(time: Long) {
        val since = time - WINDOW_MILLIS

        while (everywhere.firstOrNull()?.let { it < since } == true) everywhere.removeFirst()
        addresses.values.forEach { tries -> while (tries.firstOrNull()?.let { it < since } == true) tries.removeFirst() }
        addresses.values.removeAll { it.isEmpty() }
        usernames.values.removeAll { it.lockedUntil <= time && it.lastFailureAt < since }
    }

    private fun String.key() = trim().lowercase()

    companion object {
        val WINDOW = 15.minutes
        private val WINDOW_MILLIS = WINDOW.inWholeMilliseconds

        const val ADDRESS_MAX = 20
        const val FREE_TRIES = 5
        const val SURGE_FAILURES = 100
        val FIRST_WAIT = 30.seconds
        val LONGEST_WAIT = 15.minutes

        /** How long a username waits after its [failures]th wrong try in a row, past the free ones */
        fun waitAfter(failures: Int): Duration {
            val doublings = (failures - FREE_TRIES - 1).coerceIn(0, 10)
            return (FIRST_WAIT * (1 shl doublings)).coerceAtMost(LONGEST_WAIT)
        }
    }
}

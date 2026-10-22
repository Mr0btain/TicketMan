package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/** What a wrong password login limit is counting */
@Serializable
enum class LoginLockKind {
    /** A username, however many devices guess at it */
    USERNAME,

    /** A device, by its address (from the proxy's `X-Forwarded-For`) */
    ADDRESS
}

/**
 * A username or address refused password logins for now
 * @param key The username (lowercase) or the address
 * @param failures Wrong passwords counted against it
 * @param lockedUntil Epoch millis it can try again from
 */
@Serializable
data class LoginLock(
    val kind: LoginLockKind,
    val key: String,
    val failures: Int,
    val lockedUntil: Long
)

/**
 * The wrong password limits right now, for the admin screen
 * @param recentFailures Wrong passwords from everywhere in the last [windowMinutes]
 * @param surge Over the server wide ceiling, so every address that has had a wrong try in the window waits
 */
@Serializable
data class LoginLimitsResponse(
    val locks: List<LoginLock> = emptyList(),
    val recentFailures: Int = 0,
    val surge: Boolean = false,
    val windowMinutes: Int = 15
)

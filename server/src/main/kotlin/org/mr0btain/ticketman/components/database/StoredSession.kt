package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.server.response.SessionKind

/**
 * A logged in device
 * @param userIdentifier The user it belongs to
 * @param token Refresh token used to get new access tokens, the latest one since they're swapped on each refresh
 * @param deviceId Stable id the client sends, blank if it didn't
 * @param expiresAt Epoch millis the refresh token runs out, 0 when not known
 * @param idleTimeoutSeconds How long a kiosk PIN session lasts unused, 0 for no limit
 */
data class StoredSession(
    val sessionId: String,
    val userIdentifier: String,
    val token: String,
    val deviceId: String = "",
    val deviceName: String = "",
    val kind: SessionKind = SessionKind.PASSWORD,
    val createdAt: Long = 0,
    val lastUsedAt: Long = 0,
    val expiresAt: Long = 0,
    val idleTimeoutSeconds: Int = 0
) {
    /** Whether the session hasn't run out or sat unused past its idle limit at [now] */
    fun isLive(now: Long): Boolean =
        (expiresAt == 0L || expiresAt > now) &&
            (idleTimeoutSeconds == 0 || now - lastUsedAt <= idleTimeoutSeconds * 1_000L)
}

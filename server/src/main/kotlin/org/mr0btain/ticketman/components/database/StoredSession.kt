package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.server.response.SessionKind

/**
 * A logged in device
 * @param userIdentifier The user it belongs to
 * @param token Refresh token used to get new access tokens
 * @param deviceId Stable id the client sends, blank if it didn't
 */
data class StoredSession(
    val sessionId: String,
    val userIdentifier: String,
    val token: String,
    val deviceId: String = "",
    val deviceName: String = "",
    val kind: SessionKind = SessionKind.PASSWORD,
    val createdAt: Long = 0,
    val lastUsedAt: Long = 0
)

package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import kotlin.uuid.Uuid

/**
 * One logged in device
 * @param createdAt Epoch millis of the login that started it
 * @param lastUsedAt Epoch millis it last got a new access token
 * @param expiresAt Epoch millis the session stops working
 */
@Serializable
data class SessionResponse(
    val sessionId: String,
    val userIdentifier: Uuid,
    val username: String,
    val name: String,
    val accessLevel: AccessLevel,
    val deviceName: String,
    val kind: SessionKind,
    val createdAt: Long?,
    val lastUsedAt: Long?,
    val expiresAt: Long?
)

/** How the session was started */
@Serializable
enum class SessionKind {
    PASSWORD,
    PIN
}

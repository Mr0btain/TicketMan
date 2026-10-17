package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import kotlin.uuid.Uuid

/**
 * A user's stored refresh token, which is what keeps them logged in
 * @param issuedAt Epoch millis of the last login, null for tokens made before this was tracked
 * @param expiresAt Epoch millis the refresh token stops working
 */
@Serializable
data class SessionResponse(
    val userIdentifier: Uuid,
    val username: String,
    val name: String,
    val accessLevel: AccessLevel,
    val issuedAt: Long?,
    val expiresAt: Long?
)

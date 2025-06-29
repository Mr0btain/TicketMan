package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID

@Serializable
data class AuthResponse(
    val id: String,
    val accessToken: String,
    val refreshToken: String
)

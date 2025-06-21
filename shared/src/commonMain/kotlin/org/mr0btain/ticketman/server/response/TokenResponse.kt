package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val username: String,
    val token: String,
    val expiresAt: String
)

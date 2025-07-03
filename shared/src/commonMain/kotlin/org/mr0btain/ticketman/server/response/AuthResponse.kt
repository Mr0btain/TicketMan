package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val id: String,
    val accessToken: String,
    val refreshToken: String
)

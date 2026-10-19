package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/**
 * What `/api/auth/refresh` gives back
 * @param token The new access token
 * @param refreshToken The refresh token to use next time, the one sent is no good any more. Null from servers
 * older than rotation, which keep the old one working
 */
@Serializable
data class RefreshTokenResponse(
    val token: String,
    val refreshToken: String? = null
)

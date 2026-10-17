package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AuthResponse(
    val id: Uuid?,
    val accessToken: String?,
    val refreshToken: String?,
    val status: LoginStatus = LoginStatus.OK,
    /** Only for [LoginStatus.INVITE_SETUP] */
    val setupToken: String? = null
)

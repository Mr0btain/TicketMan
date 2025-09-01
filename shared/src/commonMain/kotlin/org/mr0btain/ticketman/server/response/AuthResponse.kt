package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class AuthResponse @OptIn(ExperimentalUuidApi::class) constructor(
    val id: Uuid?,
    val accessToken: String?,
    val refreshToken: String?
)

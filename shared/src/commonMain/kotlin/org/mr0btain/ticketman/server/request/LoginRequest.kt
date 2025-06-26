package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

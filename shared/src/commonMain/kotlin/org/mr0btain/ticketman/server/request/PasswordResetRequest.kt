package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetRequest(
    val password: String
)

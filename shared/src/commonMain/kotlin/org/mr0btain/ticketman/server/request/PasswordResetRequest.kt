package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetRequest(
    val password: String
)

/** Changing your own password, needs the one you have */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

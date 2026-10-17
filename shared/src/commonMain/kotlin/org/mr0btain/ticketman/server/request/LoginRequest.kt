package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    /** Stays the same for a device so logging in again replaces its old session instead of piling up */
    val deviceId: String = "",
    /** Shown to admins in the session list */
    val deviceName: String = ""
)

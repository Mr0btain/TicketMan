package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

@Serializable
data class UserRequest(
    val username: String,
    val password: String
)
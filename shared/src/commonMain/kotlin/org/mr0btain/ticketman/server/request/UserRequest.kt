package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.employee.AccessLevel

@Serializable
data class UserRequest(
    val username: String,
    val password: String,
    val accessLevel: AccessLevel
)
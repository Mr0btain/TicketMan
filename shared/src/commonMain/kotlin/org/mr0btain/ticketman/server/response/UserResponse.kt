package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import java.util.UUID

@Serializable
data class UserResponse(
    val identifier: String,
    val accessLevel: AccessLevel
)
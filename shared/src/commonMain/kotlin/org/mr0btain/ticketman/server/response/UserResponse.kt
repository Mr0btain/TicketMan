package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import kotlin.uuid.Uuid

@Serializable
data class UserResponse(
    val identifier: Uuid,
    val accessLevel: AccessLevel
)
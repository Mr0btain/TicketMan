package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import java.util.UUID

@Serializable
data class UserResponse(
    @Serializable(UUIDSerializer::class)
    val identifier: UUID,
    val accessLevel: AccessLevel
)
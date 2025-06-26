package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.employee.AccessLevel
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID

@Serializable
data class UserResponse(
    @Serializable(with = UUIDSerializer::class)
    val identifier: UUID,
    val accessLevel: AccessLevel
)
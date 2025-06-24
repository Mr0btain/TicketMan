package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID

@Serializable
data class RegisterResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID
)

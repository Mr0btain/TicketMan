package org.mr0btain.ticketman.data.token

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID

@Serializable
data class Token(
    @Serializable(with = UUIDSerializer::class)
    val identifier: UUID,
    val refresh: String,
    val access: String
)

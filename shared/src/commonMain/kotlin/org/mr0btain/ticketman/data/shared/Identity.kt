package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID

@Serializable
data class Identity(
    @Serializable(UUIDSerializer:: class)
    val identifier: UUID,
    val text: String,
    ) {
    override fun toString(): String = text
}
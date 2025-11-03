package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.uuid.UUIDListSerializer
import java.util.UUID

@Serializable
data class SyncRequest(
    @Serializable(with = UUIDListSerializer::class)
    val clientData: List<UUID>
)
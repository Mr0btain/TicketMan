package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.uuid.UUIDListSerializer
import java.util.UUID

@Serializable
data class SyncResponse(
    val data: List<MainItem>,
    @Serializable(UUIDListSerializer::class)
    val requested: List<UUID>
)
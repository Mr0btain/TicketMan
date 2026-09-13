package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.Uuid

@Serializable
data class SyncResponse(
    val data: List<MainItem>,
    val requested: List<Uuid>
)
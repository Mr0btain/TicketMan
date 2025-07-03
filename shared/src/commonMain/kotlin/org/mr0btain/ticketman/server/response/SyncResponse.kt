package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainDataItem

@Serializable
data class SyncResponse(
    val data: List<MainDataItem>,
    val requested: List<String>
)
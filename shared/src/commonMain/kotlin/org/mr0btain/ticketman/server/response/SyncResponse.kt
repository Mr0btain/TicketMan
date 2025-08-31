package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainDataItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class SyncResponse @OptIn(ExperimentalUuidApi::class) constructor(
    val data: List<MainDataItem>,
    val requested: List<Uuid>
)
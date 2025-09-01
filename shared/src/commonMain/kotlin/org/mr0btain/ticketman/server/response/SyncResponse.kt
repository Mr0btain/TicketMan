package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class SyncResponse @OptIn(ExperimentalUuidApi::class) constructor(
    val data: List<MainData>,
    val requested: List<Uuid>
)
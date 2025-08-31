package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class SyncRequest @OptIn(ExperimentalUuidApi::class) constructor(
    val clientData: List<Uuid>
)
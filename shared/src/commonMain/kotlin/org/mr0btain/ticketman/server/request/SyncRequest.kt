package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SyncRequest(
    val clientData: List<Uuid>
)
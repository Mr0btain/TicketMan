package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val clientData: List<String>
)
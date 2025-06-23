package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID

@Serializable
data class DataRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val dataRequested: DataRequestType
)

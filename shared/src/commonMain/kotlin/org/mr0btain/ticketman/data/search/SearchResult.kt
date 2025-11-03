package org.mr0btain.ticketman.data.search

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import java.util.UUID

@Serializable
data class SearchResult(
    @Serializable(UUIDSerializer::class)
    val identifier: UUID,
    val text: String
)

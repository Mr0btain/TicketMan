package org.mr0btain.ticketman.data.search

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.IdentifiedItem
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import java.util.UUID

@Serializable
data class SearchResult(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID,
    override val text: String
): IdentifiedItem {
    fun identity(): Identity =
        Identity(identifier, text)
}

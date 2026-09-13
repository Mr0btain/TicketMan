package org.mr0btain.ticketman.data.search

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.IdentifiedItem
import org.mr0btain.ticketman.data.shared.Identity
import kotlin.uuid.Uuid

@Serializable
data class SearchResult(
    override val identifier: Uuid,
    override val text: String
): IdentifiedItem {
    fun identity(): Identity =
        Identity(identifier, text)
}

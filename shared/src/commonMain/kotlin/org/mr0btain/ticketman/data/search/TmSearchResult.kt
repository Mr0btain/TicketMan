package org.mr0btain.ticketman.data.search

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.IdentifiedItem
import kotlin.uuid.Uuid

@Serializable
data class TmSearchResult(
    override val identifier: Uuid,
    override val text: String
): IdentifiedItem {
}

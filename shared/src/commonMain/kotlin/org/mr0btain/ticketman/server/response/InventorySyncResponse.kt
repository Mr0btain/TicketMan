package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.InventoryItem

@Serializable
data class InventorySyncResponse(
    val data: List<InventoryItem>,
    val requested: List<Int>
)
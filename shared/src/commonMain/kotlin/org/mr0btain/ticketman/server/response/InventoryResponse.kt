package org.mr0btain.ticketman.server.response

import org.mr0btain.ticketman.data.inventory.InventoryItem

data class InventoryResponse(
    val inventory: List<InventoryItem>
)

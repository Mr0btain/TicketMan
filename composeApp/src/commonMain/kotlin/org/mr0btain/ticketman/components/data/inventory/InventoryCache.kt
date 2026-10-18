package org.mr0btain.ticketman.components.data.inventory

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note

class InventoryCache(maxSize: Int): CustomCache<InventoryCache.InventoryData>(maxSize) {
    fun put(
        inventory: Inventory,
        notes: List<Note> = emptyList(),
        images: List<StoredImage> = emptyList()
    ) {
        cache.put(inventory.identifier.toString(), InventoryData(inventory, notes, images))
    }

    data class InventoryData(
        val inventory: Inventory,
        val notes: List<Note> = emptyList(),
        val images: List<StoredImage> = emptyList()
    )
}
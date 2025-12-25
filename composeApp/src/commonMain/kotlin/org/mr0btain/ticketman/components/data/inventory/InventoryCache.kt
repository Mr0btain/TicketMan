package org.mr0btain.ticketman.components.data.inventory

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import java.util.*

class InventoryCache(maxSize: Int = 15) : CustomCache<InventoryCache.InventoryData>("InventoryCache", maxSize) {
    suspend fun put(
        inventory: Inventory,
        notes: List<Note> = emptyList()
    ) {
        cache.put(
            inventory.identifier,
            InventoryData(inventory, notes)
        )
    }

    data class InventoryData(
        val inventory: Inventory,
        val notes: List<Note> = emptyList()
    )
}
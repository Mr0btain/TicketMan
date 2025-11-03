package org.mr0btain.ticketman.components.data.inventory

import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.inventory.Inventory

data class InventoryData(
    val inventory: Inventory = Inventory(),
    val notes: List<NoteData> = emptyList(),
    val images: List<ImageData> = emptyList()
)

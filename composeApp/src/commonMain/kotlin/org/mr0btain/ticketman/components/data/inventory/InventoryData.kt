package org.mr0btain.ticketman.components.data.inventory

import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.data.Inventory
import kotlin.uuid.ExperimentalUuidApi

data class InventoryData @OptIn(ExperimentalUuidApi::class) constructor(
    val inventory: Inventory = Inventory(),
    val notes: List<NoteData> = emptyList(),
    val images: List<ImageData> = emptyList()
)

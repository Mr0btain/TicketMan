package org.mr0btain.ticketman.components.data.components.inventory

import org.mr0btain.ticketman.components.data.components.shared.Note

data class InventoryItem(
    val identifier: Int = Int.MAX_VALUE,
    val name: String = "",
    val description: String = "",
    val stock: Int = Int.MAX_VALUE,
    val cost: String = "",
    val price: String = "",
    val notes: List<Note> = listOf(),
    val time: Long = Long.MAX_VALUE
)
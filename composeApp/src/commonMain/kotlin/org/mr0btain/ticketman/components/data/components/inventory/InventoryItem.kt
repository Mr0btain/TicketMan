package org.mr0btain.ticketman.components.data.components.inventory

data class InventoryItem(
    val identifier: Int = Int.MAX_VALUE,
    val name: String = "",
    val description: String = "",
    val stock: Int = Int.MAX_VALUE,
    val cost: String = "",
    val price: String = "",
    val time: Long = Long.MAX_VALUE
)
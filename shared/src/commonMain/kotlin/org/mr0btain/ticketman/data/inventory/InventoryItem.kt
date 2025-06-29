package org.mr0btain.ticketman.data.inventory

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.shared.Note

@Serializable
data class InventoryItem(
    override val identifier: String = "",
    val name: String = "",
    val description: String = "",
    val stock: Int = Int.MAX_VALUE,
    val cost: String = "",
    val price: String = "",
    val notes: List<Note> = listOf(),
    val time: Long = Long.MAX_VALUE
): MainDataItem
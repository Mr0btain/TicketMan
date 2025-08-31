package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Add images?

@Serializable
data class InventoryItem @OptIn(ExperimentalUuidApi::class) constructor(
    override val identifier: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val stock: Int = Int.MAX_VALUE,
    val cost: String = "",
    val price: String = "",
    val time: Long = Long.MAX_VALUE
): MainDataItem
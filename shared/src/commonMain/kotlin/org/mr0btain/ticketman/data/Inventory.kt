package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

// TODO: Add images?

@Serializable
data class Inventory(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val name: String = "",
    val description: String = "",
    val stock: Int = Int.MAX_VALUE,
    val cost: String = "",
    val price: String = "",
    val time: Long = Long.MAX_VALUE
): MainItem
package org.mr0btain.ticketman.data.inventory

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Inventory(
    override val identifier: Uuid = Uuid.generateV7(),
    val name: String = "",
    val description: String = "",
    val stock: Int = Int.MAX_VALUE,
    /** What we pay, in cents */
    val costCents: Long = 0,
    /** What we charge, in cents */
    val priceCents: Long = 0,
    /** The shop's tax class it's sold at, null for the main rate */
    val taxClass: String? = null
): MainItem()
package org.mr0btain.ticketman.data.inventory

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Something the shop stocks, a part or anything else it sells
 * @param sku The shop's own code for it, unique when set
 * @param barcode What a scanner reads off it (UPC, EAN or the shop's label), unique when set
 * @param category For grouping, like "Screens" or "Batteries"
 * @param reorderPoint Shows as low stock at or below this, null for the admin screen's threshold
 * @param supplierIdentifier Who it's usually ordered from
 */
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
    val taxClass: String? = null,
    val sku: String = "",
    val barcode: String = "",
    val category: String = "",
    val reorderPoint: Int? = null,
    val supplierIdentifier: Uuid? = null
): MainItem() {
    /** Whether pickups and sales count it down */
    val tracksStock: Boolean
        get() = stock != Int.MAX_VALUE

    /** At or below where it should be ordered again, [threshold] when it has no reorder point of its own */
    fun isLow(threshold: Int): Boolean = tracksStock && stock <= (reorderPoint ?: threshold)
}
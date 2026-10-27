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
 * @param stock How many there are at the location it was read for, every location added up when read for all of
 * them. [Int.MAX_VALUE] when the item's stock isn't tracked
 * @param reorderPoint Shows as low stock at or below this at that location, null for the admin screen's threshold
 * @param supplierIdentifier Who it's usually ordered from
 * @param stockAt The count at each location, filled in by the server
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
    val supplierIdentifier: Uuid? = null,
    val stockAt: List<LocationStock> = emptyList()
): MainItem() {
    /** Whether pickups and sales count it down */
    val tracksStock: Boolean
        get() = stock != Int.MAX_VALUE

    /** At or below where it should be ordered again, [threshold] when it has no reorder point of its own */
    fun isLow(threshold: Int): Boolean = tracksStock && stock <= (reorderPoint ?: threshold)
}

/** How many of an item are at one location, and when it should be ordered again there */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class LocationStock(
    val locationIdentifier: Uuid,
    val stock: Int,
    val reorderPoint: Int? = null
)
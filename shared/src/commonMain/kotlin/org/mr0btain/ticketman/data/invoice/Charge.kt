package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One line on an invoice
 * @param quantity Hours for labor, count for parts. Up to two decimals
 * @param unitPriceCents Price of one
 * @param inventoryIdentifier The inventory item for parts, its stock is taken when the invoice is paid
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Charge(
    val identifier: Uuid = Uuid.generateV7(),
    val type: ChargeType = ChargeType.OTHER,
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPriceCents: Long = 0,
    val inventoryIdentifier: Uuid? = null,
    val taxable: Boolean = true
): SubItem {
    /** Quantity times price, rounded to the cent */
    val totalCents: Long
        get() = (quantity * unitPriceCents).roundToLong()
}

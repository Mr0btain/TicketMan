package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What was done on a ticket: time spent, or a part used */
@Serializable
enum class WorkType {
    LABOR,
    PART
}

/**
 * One thing logged on a ticket's work log. Each one puts a line on the ticket's invoice with the same identifier,
 * which goes when the entry is removed while the invoice is still open
 * @param quantity Hours for labor, how many for a part. Up to two decimals, whole numbers for parts
 * @param unitPriceCents Per hour, or per part
 * @param inventoryIdentifier The inventory item a part came from
 * @param userIdentifier Who logged it, filled in by the server
 * @param workedAt When it was logged, filled in by the server
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class WorkEntry(
    val identifier: Uuid = Uuid.generateV7(),
    val type: WorkType = WorkType.LABOR,
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPriceCents: Long = 0,
    val inventoryIdentifier: Uuid? = null,
    val userIdentifier: Uuid? = null,
    val userName: String = "",
    val workedAt: Long = 0
) {
    /** Quantity times price, rounded to the cent, the same as its invoice line */
    val totalCents: Long
        get() = (quantity * unitPriceCents).roundToLong()
}

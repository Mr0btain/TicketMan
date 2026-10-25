package org.mr0btain.ticketman.data.purchase

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Somewhere the shop buys parts from
 * @param accountNumber The shop's account with them, for orders
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Supplier(
    val identifier: Uuid = Uuid.generateV7(),
    val name: String = "",
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val accountNumber: String = "",
    val notes: String = ""
)

@Serializable
enum class PurchaseStatus(val label: String) {
    /** Being put together, lines can still change */
    DRAFT("Draft"),

    /** Sent to the supplier, waiting for it to arrive */
    ORDERED("Ordered"),

    /** Some of it has arrived */
    PARTLY_RECEIVED("Partly received"),

    RECEIVED("Received"),
    CANCELLED("Cancelled");

    /** Still waiting on parts */
    val isOpen: Boolean
        get() = this == ORDERED || this == PARTLY_RECEIVED
}

/**
 * Readable purchase order number like PO-26-00000042, counted per year like invoice numbers
 */
@Serializable
data class PurchaseNumber(
    val year: Int,
    val sequence: Long,
    val prefix: String = ""
) {
    override fun toString(): String =
        "PO-" + (if (prefix.isBlank()) "" else "$prefix-") + "${(year % 100).toString().padStart(2, '0')}-${sequence.toString().padStart(8, '0')}"
}

/**
 * Some of one item on an order
 * @param ticketIdentifier The ticket it's for, when it was ordered for a repair. Receiving the last of a ticket's
 * parts moves it on
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class PurchaseLine(
    val identifier: Uuid = Uuid.generateV7(),
    val inventoryIdentifier: Uuid,
    val description: String = "",
    val quantity: Int = 1,
    val receivedQuantity: Int = 0,
    val unitCostCents: Long = 0,
    val ticketIdentifier: Uuid? = null
) {
    /** Still to arrive */
    val outstanding: Int
        get() = (quantity - receivedQuantity).coerceAtLeast(0)

    val totalCents: Long
        get() = quantity * unitCostCents
}

/**
 * An order to a supplier
 * @param supplierName The supplier's name, filled in by the server
 * @param createdAt Epoch millis, like the other times
 * @param expectedAt When the supplier said it would arrive, if they did
 * @param locationIdentifier Where it's delivered, whose stock receiving it adds to. Set by the server
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class PurchaseOrder(
    val identifier: Uuid = Uuid.generateV7(),
    val number: PurchaseNumber? = null,
    val supplierIdentifier: Uuid? = null,
    val supplierName: String = "",
    val status: PurchaseStatus = PurchaseStatus.DRAFT,
    val lines: List<PurchaseLine> = emptyList(),
    val note: String = "",
    val createdAt: Long = 0,
    val createdBy: String = "",
    val orderedAt: Long? = null,
    val receivedAt: Long? = null,
    val expectedAt: Long? = null,
    val locationIdentifier: Uuid? = null
) {
    val label: String
        get() = number?.toString() ?: identifier.toString().substringBefore("-")

    val totalCents: Long
        get() = lines.sumOf { it.totalCents }

    /** Pieces still to arrive, across every line */
    val outstanding: Int
        get() = lines.sumOf { it.outstanding }
}

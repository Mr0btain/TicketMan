package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.purchase.PurchaseLine
import kotlin.uuid.Uuid

/**
 * A draft purchase order's supplier, lines and note, for making one or changing it while it's a draft
 * @param expectedAt Epoch millis it's expected, if the supplier said
 */
@Serializable
data class PurchaseOrderRequest(
    val supplierIdentifier: Uuid? = null,
    val lines: List<PurchaseLine> = emptyList(),
    val note: String = "",
    val expectedAt: Long? = null
)

/** Parts that arrived: how many of each line */
@Serializable
data class ReceiveRequest(
    val lines: List<ReceivedLine>
)

@Serializable
data class ReceivedLine(
    val lineIdentifier: Uuid,
    val quantity: Int
)

/**
 * A part needed for a ticket: added to the supplier's draft order, or a new one
 * @param supplierIdentifier Null for the item's own supplier
 */
@Serializable
data class OrderPartRequest(
    val ticketIdentifier: Uuid,
    val inventoryIdentifier: Uuid,
    val quantity: Int = 1,
    val supplierIdentifier: Uuid? = null
)

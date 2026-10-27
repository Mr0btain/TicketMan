package org.mr0btain.ticketman.data.inventory

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parts moved from one location to another: counted down at one and up at the other together
 * @param itemName Filled in by the server
 * @param movedBy Username of who moved them
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class StockTransfer(
    val identifier: Uuid = Uuid.generateV7(),
    val itemIdentifier: Uuid,
    val itemName: String = "",
    val fromLocation: Uuid,
    val toLocation: Uuid,
    val quantity: Int,
    val note: String = "",
    val movedBy: String = "",
    val movedAt: Long = 0
)

/** Moves [quantity] of an item from [fromLocation] to [toLocation] */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class StockTransferRequest(
    val fromLocation: Uuid,
    val toLocation: Uuid,
    val quantity: Int,
    val note: String = ""
) {
    /** What's wrong with it, or null */
    fun problem(): String? = when {
        fromLocation == toLocation -> "Pick two different locations"
        quantity <= 0 -> "Move at least one"
        quantity > StockTransferRules.MAX_QUANTITY -> "Move at most ${StockTransferRules.MAX_QUANTITY} at once"
        note.length > StockTransferRules.MAX_NOTE -> "The note is too long"
        else -> null
    }
}

object StockTransferRules {
    const val MAX_QUANTITY = 100_000
    const val MAX_NOTE = 500

    /** How many of an item's moves are listed */
    const val HISTORY = 50
}

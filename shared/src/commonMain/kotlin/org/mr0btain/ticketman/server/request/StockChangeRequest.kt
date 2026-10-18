package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

/**
 * Sets an item's stock count by hand. Only goes through if the count is still [from], so a pickup that took
 * stock since the item was opened isn't overwritten
 * @param from The count when the item was opened
 * @param to The new count, zero or more. `Int.MAX_VALUE` stops tracking it
 */
@Serializable
data class StockChangeRequest(
    val from: Int,
    val to: Int
)

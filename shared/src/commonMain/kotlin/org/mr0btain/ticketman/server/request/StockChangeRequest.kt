package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sets an item's stock count by hand. Only goes through if the count is still [from], so a pickup that took
 * stock since the item was opened isn't overwritten
 * @param from The count when the item was opened
 * @param to The new count, zero or more. `Int.MAX_VALUE` stops tracking it, at every location
 * @param locationIdentifier The location whose count it is, null for the one the caller works at
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class StockChangeRequest(
    val from: Int,
    val to: Int,
    val locationIdentifier: Uuid? = null
)

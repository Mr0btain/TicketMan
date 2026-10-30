package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/**
 * Where a record is in a paged list
 * @param index How many records come before it, the `offset` that would fetch it first
 */
@Serializable
data class RecordPosition(val index: Long)

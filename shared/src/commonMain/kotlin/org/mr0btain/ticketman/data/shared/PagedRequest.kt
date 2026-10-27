package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A page of a list
 * @param location Only what's at this location, null for every one. Set by the server from where the client works
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class PagedRequest(
    val offset: Long,
    val itemsPerPage: Int,
    val location: Uuid? = null
)

package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable

@Serializable
data class PagedRequest(
    val offset: Long,
    val itemsPerPage: Int,
)

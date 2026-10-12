package org.mr0btain.ticketman.data.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchQuery(
    val query: String,
    val limit: Int
)

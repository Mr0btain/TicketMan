package org.mr0btain.ticketman.components.data.token

import kotlinx.serialization.Serializable

@Serializable
data class BearerTokens(
    val access: String,
    val refresh: String
)

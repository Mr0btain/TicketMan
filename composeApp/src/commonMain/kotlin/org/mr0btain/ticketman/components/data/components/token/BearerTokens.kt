package org.mr0btain.ticketman.components.data.components.token

import kotlinx.serialization.Serializable

@Serializable
data class BearerTokens(
    val access: String,
    val refresh: String
)

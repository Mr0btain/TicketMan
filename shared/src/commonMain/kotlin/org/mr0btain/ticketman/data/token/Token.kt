package org.mr0btain.ticketman.data.token

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val identifier: String,
    val refresh: String,
    val access: String
)

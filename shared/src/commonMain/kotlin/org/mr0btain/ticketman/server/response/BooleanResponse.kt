package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

@Serializable
data class BooleanResponse(
    val value: Boolean
)

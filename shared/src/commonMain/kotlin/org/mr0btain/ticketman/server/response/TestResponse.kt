package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

@Serializable
data class TestResponse(
    val text: String,
    val number: Int
)

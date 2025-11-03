package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable

@Serializable
data class TestRequest(
    val message: String,
    val number: Int
)

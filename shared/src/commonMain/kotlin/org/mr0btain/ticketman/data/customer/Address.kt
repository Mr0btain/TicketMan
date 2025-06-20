package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable

@Serializable
data class Address(
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = ""
)
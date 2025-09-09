package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class Address(
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = ""
): SubItem
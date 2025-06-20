package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable

@Serializable
data class PersonName(
    val firstName: String = "",
    val lastName: String = ""
)
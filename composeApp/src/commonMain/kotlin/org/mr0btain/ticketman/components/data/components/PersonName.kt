package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class PersonName(
    val firstName: String = "",
    val lastName: String = ""
)
package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class ContactInfo(
    val email: List<String> = listOf(),
    val phone: List<String> = listOf()
)
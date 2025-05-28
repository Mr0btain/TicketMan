package org.mr0btain.ticketman.components.data.components

data class ContactInfo(
    val email: String = "",
    val phone: List<String> = listOf()
)
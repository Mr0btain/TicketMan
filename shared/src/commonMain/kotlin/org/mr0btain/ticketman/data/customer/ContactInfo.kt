package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class ContactInfo(
    val email: List<String> = listOf(),
    val phone: List<String> = listOf()
): SubItem
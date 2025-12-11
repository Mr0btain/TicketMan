package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

@Serializable
data class ContactInfo(
    val email: String = "",
    val phone: String = ""
): SubItem
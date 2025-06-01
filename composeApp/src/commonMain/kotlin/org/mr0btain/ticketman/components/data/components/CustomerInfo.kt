package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class CustomerInfo(
    val identifier: String = "",
    val name: PersonName = PersonName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo()
)
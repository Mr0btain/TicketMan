package org.mr0btain.ticketman.components.data.components.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.components.data.components.shared.PersonName

@Serializable
data class CustomerInfo(
    val identifier: Int = Int.MAX_VALUE,
    val name: PersonName = PersonName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo(),
    val time: Long = Long.MAX_VALUE
)
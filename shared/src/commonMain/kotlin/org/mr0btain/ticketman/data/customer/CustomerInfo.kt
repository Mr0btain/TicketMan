package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.shared.PersonName

@Serializable
data class CustomerInfo(
    val identifier: Int = Int.MAX_VALUE,
    val name: PersonName = PersonName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo(),
    val time: Long = Long.MAX_VALUE
)
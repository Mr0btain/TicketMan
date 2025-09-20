package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

@Serializable
data class Customer(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val name: PersonName = PersonName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo(),
    val time: Long = Long.MAX_VALUE
): MainItem
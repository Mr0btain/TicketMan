package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.shared.PersonName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Customer @OptIn(ExperimentalUuidApi::class) constructor(
    override val identifier: Uuid = Uuid.random(),
    val name: PersonName = PersonName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo(),
    val time: Long = Long.MAX_VALUE
): MainData
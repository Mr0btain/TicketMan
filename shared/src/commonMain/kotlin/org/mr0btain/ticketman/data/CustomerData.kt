package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName

@Serializable
data class CustomerData(
    override val identifier: String = "",
    val name: PersonName = PersonName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo(),
    val tickets: List<String> = listOf(),
    val notes: List<Note> = listOf(),
    val time: Long = Long.MAX_VALUE
): MainDataItem

/**
Only used for serializing into json for database
 */
@Serializable
data class TicketList(val list: List<String> = listOf())
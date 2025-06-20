package org.mr0btain.ticketman.data.employee

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.customer.ContactInfo

@Serializable
data class Employee(
    val identifier: Int = Int.MAX_VALUE,
    val name: PersonName = PersonName(),
    val notes: List<Note> = listOf(),
    val contactInfo: ContactInfo = ContactInfo(),
    val tickets: List<Int> = listOf(),
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED,
    val time: Long = Long.MAX_VALUE
)
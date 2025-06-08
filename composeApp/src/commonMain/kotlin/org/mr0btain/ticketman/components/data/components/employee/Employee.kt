package org.mr0btain.ticketman.components.data.components.employee

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.components.data.components.shared.Note
import org.mr0btain.ticketman.components.data.components.shared.PersonName
import org.mr0btain.ticketman.components.data.components.customer.ContactInfo

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
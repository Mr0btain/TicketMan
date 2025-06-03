package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class Employee(
    val identifier: String = "",
    val name: PersonName = PersonName(),
    val notes: List<Note> = listOf(),
    val contactInfo: ContactInfo = ContactInfo(),
    val tickets: List<String> = listOf(),
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED,
    val time: Long
)



package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName

// TODO: Change to storing password hash instead of raw
@Serializable
data class User(
    override val identifier: String = "",
    val username: String = "",
    val password: String = "",
    val name: PersonName = PersonName(),
    val notes: List<Note> = listOf(),
    val contactInfo: ContactInfo = ContactInfo(),
    val tickets: List<String> = emptyList(),
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED,
    val time: Long = Long.MAX_VALUE
): MainDataItem

// TODO: Flesh out
fun hashPassword(password: String): String {
    return password
}

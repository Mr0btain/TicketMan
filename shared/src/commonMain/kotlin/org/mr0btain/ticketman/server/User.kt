package org.mr0btain.ticketman.server

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.employee.AccessLevel
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.util.UUIDSerializer
import java.util.UUID
import kotlin.time.Clock

// TODO: Change to storing password hash instead of raw
@Serializable
data class User(
    @Serializable(with = UUIDSerializer::class)
    val identifier: UUID = UUID.randomUUID(),
    val username: String = "",
    val password: String = "",
    val name: PersonName = PersonName(),
    val notes: List<Note> = listOf(),
    val contactInfo: ContactInfo = ContactInfo(),
    val tickets: List<Int> = listOf(),
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED,
    val time: Long = Long.MAX_VALUE
)

// TODO: Flesh out
fun hashPassword(password: String): String {
    return password
}

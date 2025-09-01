package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.PersonName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Change to storing password hash instead of raw
@Serializable
data class User @OptIn(ExperimentalUuidApi::class) constructor(
    override val identifier: Uuid = Uuid.random(),
    val username: String = "",
    val password: String = "",
    val name: PersonName = PersonName(),
    val contactInfo: ContactInfo = ContactInfo(),
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED,
    val time: Long = Long.MAX_VALUE
): MainData

// TODO: Flesh out
fun hashPassword(password: String): String {
    return password
}

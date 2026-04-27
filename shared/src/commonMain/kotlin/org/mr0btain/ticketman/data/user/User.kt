package org.mr0btain.ticketman.data.user

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

// TODO: Change to storing password hash instead of raw
@Serializable
data class User(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED
): MainItem

// TODO: Flesh out
fun hashPassword(password: String): String {
    return password
}

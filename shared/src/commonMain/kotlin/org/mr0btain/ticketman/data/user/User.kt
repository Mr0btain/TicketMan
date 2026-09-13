package org.mr0btain.ticketman.data.user

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.inventory.AccessLevel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Change to storing password hash instead of raw
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class User(
    override val identifier: Uuid = Uuid.generateV7(),
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED
): MainItem() {
    fun cleanName(): String {
        return name.split(":").joinToString(" ") { n ->
            n.replaceFirstChar { it.titlecase() }
        }
    }
}

// TODO: Flesh out
fun hashPassword(password: String): String {
    return password
}

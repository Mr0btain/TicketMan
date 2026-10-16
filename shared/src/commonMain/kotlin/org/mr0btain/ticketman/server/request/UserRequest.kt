package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Note

@Serializable
data class UserRequest(
    val username: String,
    val password: String,
    val name: String = "",
    val notes: List<Note> = listOf(),
    val phone: String = "",
    val email: String = "",
    val tickets: List<String> = listOf(),
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED
)


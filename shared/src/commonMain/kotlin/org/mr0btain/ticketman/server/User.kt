package org.mr0btain.ticketman.server

import java.util.UUID

// TODO: Store the password with encryption/hash

data class User(
    val id: UUID,
    val username: String,
    val password: String
)

// TODO: Flesh out
fun hashPassword(password: String): String {
    return password
}

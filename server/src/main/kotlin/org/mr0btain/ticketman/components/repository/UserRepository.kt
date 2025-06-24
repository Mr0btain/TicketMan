package org.mr0btain.ticketman.components.repository

import org.mr0btain.ticketman.server.User
import java.util.UUID

// TODO: Flesh this out for using database
// TODO: Encrypt values
class UserRepository {
    private val users = mutableListOf<User>()

    fun findAll(): List<User> = users

    fun findById(id: UUID): User? =
        users.firstOrNull { user -> user.identifier == id }

    fun findByUsername(username: String): User? =
        users.firstOrNull { user -> user.username == username }

    fun save(user: User): Boolean =
        users.add(user)
}
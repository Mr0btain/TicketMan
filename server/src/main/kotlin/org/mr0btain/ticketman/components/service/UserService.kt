package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.repository.UserRepository
import org.mr0btain.ticketman.server.User
import java.util.UUID

class UserService(
    private val userRepository: UserRepository
) {
    fun fundAll(): List<User> =
        userRepository.findAll()

    fun findById(id: String): User? =
        userRepository.findById(UUID.fromString(id))

    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username)

    fun save(user: User): User? {
        val foundUser = userRepository.findByUsername(user.username)

        return if (foundUser == null) {
            userRepository.save(user)
            user
        } else null
    }
}
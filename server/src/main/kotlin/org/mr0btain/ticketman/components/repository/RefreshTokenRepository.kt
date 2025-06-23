package org.mr0btain.ticketman.components.repository

class RefreshTokenRepository {
    private val tokens = mutableMapOf<String, String>()

    fun findUsernameByToken(token: String): String? =
        tokens[token]

    fun save(token: String, userName: String) {
        tokens[token] = userName
    }
}
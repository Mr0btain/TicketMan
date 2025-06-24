package org.mr0btain.ticketman.components.repository

import java.util.UUID

class RefreshTokenRepository {
    private val tokens = mutableMapOf<String, UUID>()

    fun findIdByToken(token: String): UUID? =
        tokens[token]

    fun save(token: String, id: UUID) {
        tokens[token] = id
    }
}
package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.TokenTable.identifier
import org.mr0btain.ticketman.components.database.TokenTable.token
import java.util.UUID

class TokenDatabase(
    private val shouldLog: Boolean
) {
    fun findById(uuid: UUID): RefreshToken? {
        val ident = uuid.toString()
        var found: RefreshToken? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)
                TokenTable.selectAll().where { identifier eq ident }.firstOrNull()?.let { item ->
                    found = RefreshToken(
                        identifier = UUID.fromString(item[identifier]),
                        token = item[token]
                    )
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return found
    }

    fun findByToken(refreshToken: String): RefreshToken? {
        var found: RefreshToken? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TokenTable.selectAll().where { token eq refreshToken }.firstOrNull()?.let { item ->
                    found = RefreshToken(
                        identifier = UUID.fromString(item[identifier]),
                        token = item[token]
                    )
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return found
    }

    fun add(refreshToken: RefreshToken): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TokenTable.insert { item ->
                    item[identifier] = refreshToken.identifier.toString()
                    item[token] = refreshToken.token
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }

        return true
    }

    fun update(refreshToken: RefreshToken): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TokenTable.update(where = { identifier eq refreshToken.identifier.toString()  }) { item ->
                    item[identifier] = refreshToken.identifier.toString()
                    item[token] = refreshToken.token
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }

        return true
    }

    fun remove(uuid: UUID): Boolean {
        val ident = uuid.toString()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TokenTable.deleteWhere { identifier eq ident }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }

        return true
    }
}
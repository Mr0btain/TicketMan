package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.TokenTable.identifier
import org.mr0btain.ticketman.components.database.TokenTable.token
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger

@Suppress("PrivatePropertyName")
class TokenDatabase() {
    private val TAG = "TokenDatabase"
    private val FIND_BY_ID_TAG = "$TAG:FindById"
    private val FIND_BY_TOKEN_TAG = "$TAG:FindByToken"
    private val ADD_TAG = "$TAG:Add"
    private val UPDATE_TAG = "$TAG:Update"
    private val REMOVE_TAG = "$TAG:Remove"

    fun findById(identifier: String): RefreshToken? {
        var found: RefreshToken? = null

        try {
            transaction {
                TokenTable.selectAll().where { TokenTable.identifier eq identifier }.firstOrNull()?.let { item ->
                    found = RefreshToken(
                        identifier = item[TokenTable.identifier],
                        token = item[token]
                    )
                } ?: AppLogger.i(FIND_BY_ID_TAG, SimpleResult.FAIL)
            }

            AppLogger.m(FIND_BY_ID_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.e(FIND_BY_ID_TAG, SimpleResult.FAIL, ex)
        }

        return found
    }

    fun findByToken(refreshToken: String): RefreshToken? {
        var found: RefreshToken? = null

        try {
            transaction {
                TokenTable.selectAll().where { token eq refreshToken }.firstOrNull()?.let { item ->
                    found = RefreshToken(
                        identifier = item[identifier],
                        token = item[token]
                    )
                } ?: AppLogger.i(FIND_BY_TOKEN_TAG, SimpleResult.FAIL)
            }

            AppLogger.m(FIND_BY_TOKEN_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.e(FIND_BY_TOKEN_TAG, SimpleResult.FAIL, ex)
        }

        return found
    }

    fun add(refreshToken: RefreshToken): Boolean {
        try {
            transaction {
                TokenTable.insert { item ->
                    item[identifier] = refreshToken.identifier
                    item[token] = refreshToken.token
                }
            }

            AppLogger.m(ADD_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.e(ADD_TAG, SimpleResult.FAIL, ex)
            return false
        }

        return true
    }

    fun update(refreshToken: RefreshToken): Boolean {
        try {
            transaction {
                TokenTable.update(where = { identifier eq refreshToken.identifier }) { item ->
                    item[identifier] = refreshToken.identifier
                    item[token] = refreshToken.token
                }
            }

            AppLogger.m(UPDATE_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.e(UPDATE_TAG, SimpleResult.FAIL, ex)
            return false
        }

        return true
    }

    fun remove(identifier: String): Boolean {
        try {
            transaction {
               TokenTable.deleteWhere { this.identifier eq identifier }
            }

            AppLogger.m(REMOVE_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.e(REMOVE_TAG, SimpleResult.FAIL, ex)
            return false
        }

        return true
    }
}
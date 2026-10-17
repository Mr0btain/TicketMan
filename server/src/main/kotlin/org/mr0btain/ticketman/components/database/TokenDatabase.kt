package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.server.response.SessionKind
import kotlin.time.Clock

/**
 * Stores logged in sessions, a user can have one per device
 */
@Suppress("PrivatePropertyName")
class TokenDatabase() {
    private val TAG = "TokenDatabase"
    private val FIND_BY_SESSION_TAG = "$TAG:FindBySession"
    private val FIND_BY_TOKEN_TAG = "$TAG:FindByToken"
    private val ADD_TAG = "$TAG:Add"
    private val TOUCH_TAG = "$TAG:Touch"
    private val REMOVE_TAG = "$TAG:Remove"
    private val REMOVE_FOR_USER_TAG = "$TAG:RemoveForUser"
    private val GET_ALL_TAG = "$TAG:GetAll"
    private val COUNT_TAG = "$TAG:Count"
    private val CLEAR_LEGACY_TAG = "$TAG:ClearLegacy"

    fun findBySessionId(sessionId: String): StoredSession? {
        if (sessionId.isBlank()) return null

        return appLogTryCatch<StoredSession?>(FIND_BY_SESSION_TAG) {
            transaction {
                val found = TokenTable.selectAll().where { TokenTable.sessionId eq sessionId }.firstOrNull()?.toSession()
                Pair(found, found != null)
            }
        }
    }

    fun findByToken(refreshToken: String): StoredSession? {
        return appLogTryCatch<StoredSession?>(FIND_BY_TOKEN_TAG) {
            transaction {
                val found = TokenTable.selectAll().where { TokenTable.token eq refreshToken }.firstOrNull()?.toSession()
                Pair(found, found != null)
            }
        }
    }

    /**
     * Saves a new session. A device that logs in again replaces its old session for that user
     */
    fun add(session: StoredSession): Boolean {
        return appLogTryCatch<Boolean>(ADD_TAG) {
            transaction {
                if (session.deviceId.isNotBlank()) {
                    TokenTable.deleteWhere {
                        (TokenTable.identifier eq session.userIdentifier) and (TokenTable.deviceId eq session.deviceId)
                    }
                }

                TokenTable.insert { row ->
                    row[identifier] = session.userIdentifier
                    row[token] = session.token
                    row[sessionId] = session.sessionId
                    row[deviceId] = session.deviceId
                    row[deviceName] = session.deviceName
                    row[kind] = session.kind.name
                    row[createdAt] = session.createdAt
                    row[lastUsedAt] = session.lastUsedAt
                }

                Pair(true, true)
            }
        } ?: false
    }

    /** Marks a session as just used */
    fun touch(sessionId: String) {
        appLogTryCatch(TOUCH_TAG) {
            transaction {
                TokenTable.update(where = { TokenTable.sessionId eq sessionId }) { row ->
                    row[lastUsedAt] = Clock.System.now().toEpochMilliseconds()
                } > 0
            }
        }
    }

    /** Ends one session, false if it didn't exist */
    fun remove(sessionId: String): Boolean {
        return appLogTryCatch<Boolean>(REMOVE_TAG) {
            transaction {
                val removed = TokenTable.deleteWhere { TokenTable.sessionId eq sessionId } > 0
                Pair(removed, removed)
            }
        } ?: false
    }

    /**
     * Ends every session a user has
     * @param keepSessionId A session to leave alone, like the one making the request
     */
    fun removeAllForUser(userIdentifier: String, keepSessionId: String? = null) {
        appLogTryCatch(REMOVE_FOR_USER_TAG) {
            transaction {
                TokenTable.deleteWhere {
                    val forUser = TokenTable.identifier eq userIdentifier
                    if (keepSessionId != null) forUser and (TokenTable.sessionId neq keepSessionId) else forUser
                }
            }

            true
        }
    }

    fun getAll(): List<StoredSession> {
        return appLogTryCatch<List<StoredSession>>(GET_ALL_TAG) {
            transaction {
                Pair(TokenTable.selectAll().map { it.toSession() }, true)
            }
        }.orEmpty()
    }

    fun count(): Long {
        return appLogTryCatch<Long>(COUNT_TAG) {
            transaction {
                Pair(TokenTable.selectAll().count(), true)
            }
        } ?: 0
    }

    /** Drops sessions from before sessions had ids, those users just log in again */
    fun clearLegacySessions() {
        appLogTryCatch(CLEAR_LEGACY_TAG) {
            transaction {
                TokenTable.deleteWhere { TokenTable.sessionId eq "" }
            }

            true
        }
    }

    private fun ResultRow.toSession(): StoredSession =
        StoredSession(
            sessionId = this[TokenTable.sessionId],
            userIdentifier = this[TokenTable.identifier],
            token = this[TokenTable.token],
            deviceId = this[TokenTable.deviceId],
            deviceName = this[TokenTable.deviceName],
            kind = SessionKind.entries.firstOrNull { it.name == this[TokenTable.kind] } ?: SessionKind.PASSWORD,
            createdAt = this[TokenTable.createdAt],
            lastUsedAt = this[TokenTable.lastUsedAt]
        )
}

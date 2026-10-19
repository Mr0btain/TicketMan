package org.mr0btain.ticketman.components.database

import com.auth0.jwt.JWT
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.*
import org.mr0btain.ticketman.server.response.SessionKind
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

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
    private val ROTATE_TAG = "$TAG:Rotate"
    private val IDLE_TAG = "$TAG:ClearIdle"
    private val EXPIRED_TAG = "$TAG:RemoveExpired"
    private val BACKFILL_TAG = "$TAG:BackfillExpiry"

    fun findBySessionId(sessionId: String): StoredSession? {
        if (sessionId.isBlank()) return null

        return dbQuery(FIND_BY_SESSION_TAG) {
            TokenTable.selectAll().where { TokenTable.sessionId eq sessionId }.firstOrNull()?.toSession()
        }
    }

    fun findByToken(refreshToken: String): StoredSession? {
        return dbQuery(FIND_BY_TOKEN_TAG) {
            TokenTable.selectAll().where { TokenTable.token eq refreshToken }.firstOrNull()?.toSession()
        }
    }

    /**
     * Saves a new session. A device that logs in again replaces its old session for that user. Logins with no
     * device id (scripts, old clients) can't be told apart, so only the newest [MAX_UNNAMED_SESSIONS] of those
     * are kept per user
     */
    fun add(session: StoredSession): Boolean {
        return dbWrite(ADD_TAG) {
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
                row[expiresAt] = session.expiresAt
                row[idleTimeoutSeconds] = session.idleTimeoutSeconds
            }

            if (session.deviceId.isBlank()) {
                val unnamed = (TokenTable.identifier eq session.userIdentifier) and (TokenTable.deviceId eq "")
                val oldest = TokenTable.select(TokenTable.sessionId)
                    .where { unnamed }
                    .orderBy(TokenTable.createdAt to SortOrder.DESC, TokenTable.id to SortOrder.DESC)
                    .offset(MAX_UNNAMED_SESSIONS.toLong())
                    .map { it[TokenTable.sessionId] }

                if (oldest.isNotEmpty()) TokenTable.deleteWhere { TokenTable.sessionId inList oldest }
            }

            true
        }
    }

    /**
     * Swaps a session's refresh token for a new one. Only works while [oldToken] is still the current one, so of
     * two refreshes with the same token only the first gets through
     * @return Whether it was swapped
     */
    fun rotate(sessionId: String, oldToken: String, newToken: String): Boolean {
        return dbQuery(ROTATE_TAG) {
            val swapped = TokenTable.update(where = { (TokenTable.sessionId eq sessionId) and (TokenTable.token eq oldToken) }) { row ->
                row[token] = newToken
                row[lastUsedAt] = Clock.System.now().toEpochMilliseconds()
            } > 0

            swapped
        } ?: false
    }

    /** Takes a session's idle limit off, for when an admin leaves kiosk mode and keeps using the device */
    fun clearIdleLimit(sessionId: String): Boolean {
        return dbQuery(IDLE_TAG) {
            val changed = TokenTable.update(where = { TokenTable.sessionId eq sessionId }) { row ->
                row[idleTimeoutSeconds] = 0
            } > 0

            changed
        } ?: false
    }

    /**
     * Deletes sessions whose refresh token has run out, and kiosk PIN sessions left unused past their idle limit
     * @return How many were removed
     */
    fun removeExpired(now: Long = Clock.System.now().toEpochMilliseconds()): Int {
        return dbQuery(EXPIRED_TAG) {
            var removed = TokenTable.deleteWhere { (TokenTable.expiresAt greater 0L) and (TokenTable.expiresAt less now) }

            val idle = TokenTable.selectAll().where { TokenTable.idleTimeoutSeconds greater 0 }
                .map { it.toSession() }
                .filter { !it.isLive(now) }
                .map { it.sessionId }
            if (idle.isNotEmpty()) removed += TokenTable.deleteWhere { TokenTable.sessionId inList idle }

            removed
        } ?: 0
    }

    /** Fills in [TokenTable.expiresAt] for sessions saved before it was kept, from their refresh token */
    fun backfillExpiry() {
        dbWrite(BACKFILL_TAG) {
            TokenTable.selectAll().where { TokenTable.expiresAt eq 0L }.forEach { row ->
                val fromToken = try {
                    JWT.decode(row[TokenTable.token]).expiresAt?.time
                } catch (ex: Exception) {
                    null
                }

                // A token that can't be read can't be refreshed either, so it may as well go now
                val expiry = fromToken ?: (row[TokenTable.createdAt] + LEGACY_LIFETIME.inWholeMilliseconds)
                TokenTable.update(where = { TokenTable.id eq row[TokenTable.id] }) { it[expiresAt] = expiry }
            }

            true
        }
    }

    /**
     * Marks a session as just used, unless that was done in the last [TOUCH_INTERVAL]. Called on every request,
     * so a kiosk's idle limit only counts time nobody used it
     */
    fun touchIfStale(session: StoredSession, now: Long = Clock.System.now().toEpochMilliseconds()) {
        if (now - session.lastUsedAt >= TOUCH_INTERVAL.inWholeMilliseconds) touch(session.sessionId)
    }

    /** Marks a session as just used */
    fun touch(sessionId: String) {
        dbWrite(TOUCH_TAG) {
            TokenTable.update(where = { TokenTable.sessionId eq sessionId }) { row ->
                row[lastUsedAt] = Clock.System.now().toEpochMilliseconds()
            } > 0
        }
    }

    /** Ends one session, false if it didn't exist */
    fun remove(sessionId: String): Boolean {
        return dbQuery(REMOVE_TAG) {
            TokenTable.deleteWhere { TokenTable.sessionId eq sessionId } > 0
        } ?: false
    }

    /**
     * Ends every session a user has
     * @param keepSessionId A session to leave alone, like the one making the request
     */
    fun removeAllForUser(userIdentifier: String, keepSessionId: String? = null) {
        dbWrite(REMOVE_FOR_USER_TAG) {
            TokenTable.deleteWhere {
                val forUser = TokenTable.identifier eq userIdentifier
                if (keepSessionId != null) forUser and (TokenTable.sessionId neq keepSessionId) else forUser
            }

            true
        }
    }

    /** Ends the PIN sessions on a device, so only whoever PINs in last is signed in at a station */
    fun removePinSessionsForDevice(deviceId: String) {
        if (deviceId.isBlank()) return

        dbWrite(REMOVE_FOR_USER_TAG) {
            TokenTable.deleteWhere {
                (TokenTable.deviceId eq deviceId) and (TokenTable.kind eq SessionKind.PIN.name)
            }

            true
        }
    }

    fun getAll(): List<StoredSession> {
        return dbQuery(GET_ALL_TAG) {
            TokenTable.selectAll().map { it.toSession() }
        }.orEmpty()
    }

    fun count(): Long {
        return dbQuery(COUNT_TAG) {
            TokenTable.selectAll().count()
        } ?: 0
    }

    /** Drops sessions from before sessions had ids, those users just log in again */
    fun clearLegacySessions() {
        dbWrite(CLEAR_LEGACY_TAG) {
            TokenTable.deleteWhere { TokenTable.sessionId eq "" }
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
            lastUsedAt = this[TokenTable.lastUsedAt],
            expiresAt = this[TokenTable.expiresAt],
            idleTimeoutSeconds = this[TokenTable.idleTimeoutSeconds]
        )

    companion object {
        /** Sessions kept per user for logins that sent no device id */
        const val MAX_UNNAMED_SESSIONS = 10

        private val TOUCH_INTERVAL = 30.seconds
        // What refresh tokens lasted before the expiry was kept
        private val LEGACY_LIFETIME = 365.days
    }
}

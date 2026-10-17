package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.user.InviteTable
import org.mr0btain.ticketman.components.service.PasswordHasher
import org.mr0btain.ticketman.error.appLogTryCatch
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Setup codes handed to invited users
 */
@Suppress("PrivatePropertyName")
class InviteDatabase {
    private val TAG = "InviteDatabase"
    private val CREATE_TAG = "$TAG:Create"
    private val GET_EXPIRY_TAG = "$TAG:GetExpiry"
    private val CHECK_TAG = "$TAG:Check"
    private val REMOVE_TAG = "$TAG:Remove"

    private val random = SecureRandom()

    /**
     * Makes a new code for a user, replacing any they had
     * @return The code and when it expires, the code is never stored in the clear
     */
    fun create(userIdentifier: Uuid, createdBy: String, lifetime: Duration = INVITE_LIFETIME): Pair<String, Long>? {
        val code = generateCode()
        val expiresAt = (Clock.System.now() + lifetime).toEpochMilliseconds()

        return appLogTryCatch<Pair<String, Long>>(CREATE_TAG) {
            transaction {
                InviteTable.deleteWhere { InviteTable.user eq userIdentifier }
                InviteTable.insert { row ->
                    row[InviteTable.user] = userIdentifier
                    row[InviteTable.codeHash] = PasswordHasher.hash(code)
                    row[InviteTable.expiresAt] = expiresAt
                    row[InviteTable.createdBy] = createdBy
                }

                Pair(Pair(code, expiresAt), true)
            }
        }
    }

    /** When the user's code runs out, null if they don't have one */
    fun expiresAt(userIdentifier: Uuid): Long? {
        return appLogTryCatch<Long?>(GET_EXPIRY_TAG) {
            transaction {
                val expires = InviteTable.selectAll().where { InviteTable.user eq userIdentifier }.firstOrNull()?.get(InviteTable.expiresAt)
                Pair(expires, true)
            }
        }
    }

    /** Whether the user has an invite at all, used or not */
    fun exists(userIdentifier: Uuid): Boolean = expiresAt(userIdentifier) != null

    /** Whether [code] is the user's code and hasn't expired. Spaces and case don't matter */
    fun check(userIdentifier: Uuid, code: String): Boolean {
        return appLogTryCatch<Boolean>(CHECK_TAG) {
            transaction {
                val row = InviteTable.selectAll().where { InviteTable.user eq userIdentifier }.firstOrNull()

                val valid = row != null &&
                    row[InviteTable.expiresAt] > Clock.System.now().toEpochMilliseconds() &&
                    PasswordHasher.verify(normalize(code), row[InviteTable.codeHash])

                Pair(valid, valid)
            }
        } ?: false
    }

    fun remove(userIdentifier: Uuid) {
        appLogTryCatch(REMOVE_TAG) {
            transaction {
                InviteTable.deleteWhere { InviteTable.user eq userIdentifier }
            }

            true
        }
    }

    // Letters and numbers that can't be mixed up when read out loud or off a screen
    private fun generateCode(): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private fun normalize(code: String): String =
        code.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    companion object {
        val INVITE_LIFETIME = 72.hours
        const val CODE_LENGTH = 8
        private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}

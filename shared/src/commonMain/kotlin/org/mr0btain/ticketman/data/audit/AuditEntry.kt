package org.mr0btain.ticketman.data.audit

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A record of something a user did
 * @param actorIdentifier User that did it, null when it wasn't a logged in user (like a failed login)
 * @param entityType What kind of thing was changed, e.g. "customer"
 * @param time Epoch millis
 */
@Serializable
data class AuditEntry(
    val identifier: Uuid,
    val actorIdentifier: Uuid?,
    val actorName: String,
    val action: AuditAction,
    val entityType: String,
    val entityIdentifier: String?,
    val details: String,
    val time: Long
)

@Serializable
enum class AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    LOGIN,
    LOGIN_FAILED,
    PASSWORD_RESET,
    SESSION_REVOKE,
    SETTINGS_UPDATE,
    BACKUP,
    SEED
}

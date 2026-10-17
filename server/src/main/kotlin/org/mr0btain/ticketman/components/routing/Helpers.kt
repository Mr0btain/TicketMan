package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import kotlin.uuid.Uuid

fun RoutingCall.getIdentifierFromToken(): Uuid? {
    val principal = principal<JWTPrincipal>()
    return principal?.getClaim("identifier", String::class)
        ?.let { Uuid.parse(it) }
}

/**
 * Parses a uuid path parameter, null if it's missing or malformed
 */
fun RoutingCall.uuidParameter(name: String): Uuid? =
    parameters[name]?.let { Uuid.parseOrNull(it) }

/**
 * Username of whoever made this call
 */
fun RoutingCall.username(users: UserDatabaseImpl): String =
    getIdentifierFromToken()?.let { users.getById(it)?.username } ?: "unknown"

/**
 * Records an action by whoever made this call
 */
fun RoutingCall.audit(
    auditLog: AuditLogDatabase,
    action: AuditAction,
    entityType: String,
    entityIdentifier: Any? = null,
    details: String = ""
) {
    auditLog.log(getIdentifierFromToken(), action, entityType, entityIdentifier?.toString(), details)
}

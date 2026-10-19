package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.server.ApiQuery
import kotlin.uuid.Uuid

fun RoutingCall.getIdentifierFromToken(): Uuid? {
    val principal = principal<JWTPrincipal>()
    return principal?.getClaim("identifier", String::class)
        ?.let { Uuid.parse(it) }
}

/** Session the calling token belongs to */
fun RoutingCall.getSessionIdFromToken(): String? =
    principal<JWTPrincipal>()?.getClaim(JwtService.SESSION_CLAIM, String::class)

/**
 * Parses a uuid path parameter, null if it's missing or malformed
 */
fun RoutingCall.uuidParameter(name: String): Uuid? =
    parameters[name]?.let { Uuid.parseOrNull(it) }

/**
 * Username of whoever made this call
 */
fun RoutingCall.username(users: UserDatabase): String =
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

/**
 * The `?offset=&limit=` of a paged read. Missing ones get their defaults, and the limit is kept to [MAX_PAGE_SIZE]
 * @return Null when either isn't a number, or is below zero
 */
fun RoutingCall.pagedRequest(): PagedRequest? {
    val offset = queryParameters[ApiQuery.OFFSET]?.let { it.toLongOrNull() ?: return null } ?: 0L
    val limit = queryParameters[ApiQuery.LIMIT]?.let { it.toIntOrNull() ?: return null } ?: DEFAULT_PAGE_SIZE

    if (offset < 0 || limit < 0) return null
    return PagedRequest(offset, limit.coerceAtMost(MAX_PAGE_SIZE))
}

/**
 * The `?query=&limit=` of a search. A missing limit gets the default, and it's kept to [MAX_PAGE_SIZE]
 * @return Null when the limit isn't a number, or is below zero
 */
fun RoutingCall.searchQuery(): SearchQuery? {
    val limit = queryParameters[ApiQuery.LIMIT]?.let { it.toIntOrNull() ?: return null } ?: DEFAULT_SEARCH_LIMIT

    if (limit < 0) return null
    return SearchQuery(queryParameters[ApiQuery.QUERY].orEmpty(), limit.coerceAtMost(MAX_PAGE_SIZE))
}

/** What a paged read or search answers when its query parameters aren't numbers */
suspend fun RoutingCall.respondBadQuery() =
    respond(HttpStatusCode.BadRequest, "${ApiQuery.OFFSET} and ${ApiQuery.LIMIT} have to be whole numbers, not below zero")

const val DEFAULT_PAGE_SIZE = 50
const val DEFAULT_SEARCH_LIMIT = 20
const val MAX_PAGE_SIZE = 200

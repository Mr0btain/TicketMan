package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.PasswordResetRequest
import org.mr0btain.ticketman.server.response.UserResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * User routes. Passwords are never sent back, users come out with a blank password.
 * Changing users is admin only, and the server keeps at least one admin around.
 */

// TODO: Handle encryption in the rest of these routes
@OptIn(ExperimentalUuidApi::class)
fun Route.userRoute(
    database: UserDatabaseImpl,
    tokenDatabase: TokenDatabase,
    auditLog: AuditLogDatabase
) {
    val TAG = "UserRoute"
    val GET_TAG = "$TAG:Get"
    val ME_TAG = "$TAG:Me"
    val FETCH_TAG = "$TAG:Fetch"
    val ADD_TAG = "$TAG:Add"
    val UPDATE_TAG = "$TAG:Update"
    val PASSWORD_TAG = "$TAG:Password"
    val REMOVE_TAG = "$TAG:Remove"
    val SEARCH_TAG = "$TAG:Search"
    val ENTITY_TYPE = "user"

    /** Whether taking [target]'s admin away would leave the server with no active admins */
    fun isLastAdmin(target: User): Boolean =
        target.canAccess(AccessLevel.ADMIN) && database.countActiveAdmins() <= 1

    // Logged in user, any active access level can see themselves
    authenticate("restricted") {
        get("/me") {
            val identifier = call.getIdentifierFromToken()
            val result = identifier?.let { database.getById(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result.withoutPassword())
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(ME_TAG, "$identifier not found")
            }
        }
    }

    authenticate {
        post("/get") {
            val request = call.receive<Uuid>()

            val result = database.getById(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result.withoutPassword())
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/fetch") {
            val request = call.receive<PagedRequest>()

            val result = database.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result.map { it.withoutPassword() })
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(FETCH_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/search") {
            val request = call.receive<SearchQuery>()

            val result = database.search(request.query, request.limit)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(SEARCH_TAG, "$request not found")
            }
        }
    }

    // Add a new user, the password comes in plain and gets hashed by the database
    authenticate("admin") {
        post("/add") {
            val user = call.receive<User>()

            when {
                user.username.isBlank() || user.password.isBlank() -> {
                    call.respond(HttpStatusCode.BadRequest, "Username and password are required")
                }

                database.getByUsername(user.username) != null -> {
                    call.respond(HttpStatusCode.Conflict, "Username is already taken")
                    AppLogger.i(ADD_TAG, "${user.username} already exists")
                }

                user.accessLevel == AccessLevel.NONE -> {
                    call.respond(HttpStatusCode.BadRequest, "Pick an access level")
                }

                else -> {
                    // Users made by an admin are ready to go
                    database.add(user.copy(status = UserStatus.ACTIVE))
                    call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, user.identifier, "Added ${user.username} as ${user.accessLevel}")
                    call.respond(HttpStatusCode.Created, UserResponse(user.identifier, user.accessLevel))
                }
            }
        }
    }

    // Update everything but the password, which has its own route
    authenticate("admin") {
        post("/update") {
            val user = call.receive<User>()
            val existing = database.getById(user.identifier)
            val sameUsername = database.getByUsername(user.username)

            when {
                existing == null -> call.respond(HttpStatusCode.NotFound)

                user.username.isBlank() -> call.respond(HttpStatusCode.BadRequest, "Username is required")

                sameUsername != null && sameUsername.identifier != user.identifier -> {
                    call.respond(HttpStatusCode.Conflict, "Username is already taken")
                }

                user.accessLevel == AccessLevel.NONE -> {
                    call.respond(HttpStatusCode.BadRequest, "Deactivate users with their status instead")
                }

                // Pending users are approved through their own route
                user.status == UserStatus.PENDING && existing.status != UserStatus.PENDING -> {
                    call.respond(HttpStatusCode.BadRequest, "Users can't be set back to pending")
                }

                call.getIdentifierFromToken() == user.identifier &&
                    (user.accessLevel != existing.accessLevel || user.status != existing.status) -> {
                    call.respond(HttpStatusCode.Conflict, "You can't change your own access or status")
                }

                !user.canAccess(AccessLevel.ADMIN) && isLastAdmin(existing) -> {
                    call.respond(HttpStatusCode.Conflict, "There has to be at least one admin")
                }

                else -> {
                    // Pending users stay pending until approved
                    val saved = if (existing.status == UserStatus.PENDING) user.copy(status = UserStatus.PENDING) else user
                    database.update(saved.withoutPassword())

                    // Deactivating should also end their sessions
                    if (saved.status != UserStatus.ACTIVE) tokenDatabase.removeAllForUser(user.identifier.toString())

                    val details = when {
                        existing.status != saved.status -> "Status ${existing.status} -> ${saved.status}"
                        existing.accessLevel != saved.accessLevel -> "Access ${existing.accessLevel} -> ${saved.accessLevel}"
                        else -> "Updated details"
                    }

                    call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, user.identifier, "${user.username}: $details")
                    call.respond(HttpStatusCode.Accepted)
                    AppLogger.m(UPDATE_TAG, "${user.identifier} updated")
                }
            }
        }
    }

    // Reset a password, which also logs that user out everywhere
    authenticate("admin") {
        post("/password/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val request = call.receive<PasswordResetRequest>()

            when {
                identifier == null -> call.respond(HttpStatusCode.BadRequest)

                request.password.isBlank() -> call.respond(HttpStatusCode.BadRequest, "Password is required")

                !database.updatePassword(identifier, request.password) -> call.respond(HttpStatusCode.NotFound)

                else -> {
                    // Log them out everywhere, except the session making the change if they changed their own
                    tokenDatabase.removeAllForUser(identifier.toString(), keepSessionId = call.getSessionIdFromToken())

                    call.audit(auditLog, AuditAction.PASSWORD_RESET, ENTITY_TYPE, identifier)
                    call.respond(HttpStatusCode.Accepted)
                    AppLogger.m(PASSWORD_TAG, "$identifier password reset")
                }
            }
        }
    }

    // Bulk add mostly for syncing
    authenticate("admin") {
        post("/add/all") {
            try {
                val users = call.receive<List<User>>()

                database.addAll(users)
                call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, details = "Bulk added ${users.size} users")
                call.respond(HttpStatusCode.Accepted)
            } catch (ex: Exception) {
                AppLogger.e(ADD_TAG, "Bulk add failed", ex)
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Remove
    authenticate("admin") {
        delete("/remove/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val existing = identifier?.let { database.getById(it) }

            when {
                identifier == null || existing == null -> call.respond(HttpStatusCode.NotFound)

                call.getIdentifierFromToken() == identifier -> {
                    call.respond(HttpStatusCode.Conflict, "You can't delete yourself")
                }

                isLastAdmin(existing) -> call.respond(HttpStatusCode.Conflict, "There has to be at least one admin")

                else -> {
                    tokenDatabase.removeAllForUser(identifier.toString())
                    database.remove(identifier)

                    call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier, "Removed ${existing.username}")
                    call.respond(HttpStatusCode.Accepted)
                    AppLogger.m(REMOVE_TAG, "$identifier removed")
                }
            }
        }
    }
}

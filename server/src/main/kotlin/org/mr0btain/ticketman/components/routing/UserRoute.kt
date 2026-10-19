package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.PasswordRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.AccountSetupRequest
import org.mr0btain.ticketman.server.request.ApproveRequest
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.request.SetPinRequest
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
    database: UserDatabase,
    tokenDatabase: TokenDatabase,
    accountService: AccountService,
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

    // Logged in user, also reachable while finishing first login setup
    authenticate("setup") {
        // First login after an approved request
        post("/setup") {
            val identifier = call.getIdentifierFromToken()

            if (identifier == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            call.respondAccount(accountService.completeSetup(identifier, call.receive<AccountSetupRequest>()))
        }

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
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(FETCH_TAG, "Couldn't read $request")
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
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(SEARCH_TAG, "Couldn't read $request")
            }
        }
    }

    // ------------------------------------------ PINs ------------------------------------------

    authenticate("restricted") {
        // Change your own PIN, needs your password
        post("/me/pin") {
            val identifier = call.getIdentifierFromToken()

            if (identifier == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            call.respondAccount(accountService.setOwnPin(identifier, call.receive<SetPinRequest>()))
        }
    }

    authenticate("admin") {
        post("/pin/{identifier}/unlock") {
            val identifier = call.uuidParameter("identifier")

            if (identifier != null && database.unlockPin(identifier)) {
                call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Unlocked PIN")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Clears the PIN, they set a new one from their settings
        delete("/pin/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier != null && database.clearPin(identifier)) {
                call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Reset PIN")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // ------------------------------------------ New accounts ------------------------------------------

    authenticate("admin") {
        // Requests and invites waiting on someone
        get("/pending") {
            call.respond(HttpStatusCode.OK, accountService.pending())
        }

        post("/approve/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(accountService.approve(identifier, call.receive<ApproveRequest>().accessLevel, call.getIdentifierFromToken()))
        }

        // Turns down a request or cancels an invite
        delete("/reject/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            call.respondAccount(accountService.reject(identifier, call.getIdentifierFromToken()))
        }

        post("/invite") {
            call.respondAccount(
                accountService.invite(call.receive<InviteRequest>(), call.getIdentifierFromToken(), call.username(database)),
                success = HttpStatusCode.Created
            )
        }

        post("/invite/{identifier}/renew") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(accountService.renewInvite(identifier, call.getIdentifierFromToken(), call.username(database)))
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

                UsernameRules.problem(user.username) != null -> {
                    call.respond(HttpStatusCode.BadRequest, UsernameRules.problem(user.username)!!)
                }

                PasswordRules.problem(user.password) != null -> {
                    call.respond(HttpStatusCode.BadRequest, PasswordRules.problem(user.password)!!)
                }

                database.getByUsername(user.username) != null -> {
                    call.respond(HttpStatusCode.Conflict, "Username is already taken")
                    AppLogger.i(ADD_TAG, "${user.username} already exists")
                }

                user.accessLevel == AccessLevel.NONE -> {
                    call.respond(HttpStatusCode.BadRequest, "Pick an access level")
                }

                // Users made by an admin are ready to go
                !database.add(user.copy(status = UserStatus.ACTIVE)) -> {
                    call.respond(HttpStatusCode.InternalServerError, "The user couldn't be saved")
                }

                else -> {
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

                // Only a changed username is checked, so older accounts named before the rules can still be edited
                user.username.trim().lowercase() != existing.username && UsernameRules.problem(user.username) != null -> {
                    call.respond(HttpStatusCode.BadRequest, UsernameRules.problem(user.username)!!)
                }

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

                    if (!database.update(saved.withoutPassword())) {
                        call.respond(HttpStatusCode.InternalServerError, "The user couldn't be saved")
                        return@post
                    }

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

                PasswordRules.problem(request.password) != null -> {
                    call.respond(HttpStatusCode.BadRequest, PasswordRules.problem(request.password)!!)
                }

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

                !database.remove(identifier) -> call.respond(HttpStatusCode.InternalServerError, "The user couldn't be removed")

                else -> {
                    tokenDatabase.removeAllForUser(identifier.toString())

                    call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier, "Removed ${existing.username}")
                    call.respond(HttpStatusCode.Accepted)
                    AppLogger.m(REMOVE_TAG, "$identifier removed")
                }
            }
        }
    }

    // Staff notes, like training or schedule. Only admins can see or write them
    noteRoute(database, database, auditLog, ENTITY_TYPE, readAuth = "admin", writeAuth = "admin")
}

/**
 * Sends back an account action's result. Reified so the body has a real type to serialize, Unit sends no body
 */
suspend inline fun <reified T : Any> RoutingCall.respondAccount(
    result: AccountResult<T>,
    success: HttpStatusCode = HttpStatusCode.Accepted
) {
    when (result) {
        is AccountResult.Done -> if (result.value is Unit) respond(success) else respond(success, result.value)
        is AccountResult.Invalid -> respond(HttpStatusCode.BadRequest, result.message)
        is AccountResult.Conflict -> respond(HttpStatusCode.Conflict, result.message)
        AccountResult.NotFound -> respond(HttpStatusCode.NotFound)
    }
}

package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.hashPassword
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.request.UserRequest
import org.mr0btain.ticketman.server.response.BooleanResponse
import org.mr0btain.ticketman.server.response.SyncResponse
import org.mr0btain.ticketman.server.response.UserResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * User routes
 */

// TODO: Not sure if we should let this handle filtering restricted data, or the database itself.
// TODO: Handle encryption in the rest of these routes
@OptIn(ExperimentalUuidApi::class)
fun Route.userRoute(
    database: UserDatabaseImpl
) {
    val TAG = "UserRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val DELETE_TAG = "$TAG:Delete"
    val UPDATE_TAG = "$TAG:Update"
    val SEARCH_TAG = "$TAG:Search"

    authenticate {
        post("/get") {
            val request = call.receive<Uuid>()

            val result = database.getById(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
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
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(FETCH_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/search") {
            val request = call.receive<String>()

            val result = database.search(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(SEARCH_TAG, "$request not found")
            }
        }
    }

    authenticate("admin") {
        post("/register") {
            val userRequest = call.receive<UserRequest>()
            val identifier = Uuid.generateV7()


            val user = userRequest.toModel(identifier)
            val userExists = database.all()?.map { it.username }?.contains(user.username)

            if (userExists != null && !userExists) {
                database.add(user)
                call.respond(HttpStatusCode.Created, user.toResponse())
            } else call.respond(HttpStatusCode.Conflict)
        }
    }

    // Update
    authenticate("admin") {
        post("/update") {
            try {
                val user = call.receive<User>()

                database.update(user)
                call.respond(HttpStatusCode.Accepted)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Bulk add mostly for syncing
    authenticate("admin") {
        post("/add/all") {
            try {
                val customers = call.receive<List<User>>()

                database.addAll(customers)
                call.respond(HttpStatusCode.Accepted)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Delete
    authenticate("admin") {
        delete("/delete/{userIdentifier}") {
            try {
                val identifier = call.parameters["userIdentifier"]

                if (identifier == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }

                database.remove(Uuid.parse(identifier))
                call.respond(HttpStatusCode.Accepted)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

private fun UserRequest.toModel(id: Uuid): User =
    User(
        identifier = id,
        username = username,
        password = hashPassword(password),
        name = name,
        email = email,
        phone = phone,
        accessLevel = accessLevel
    )

private fun User.toResponse(): UserResponse =
    UserResponse(
        identifier = identifier,
        accessLevel = accessLevel
    )
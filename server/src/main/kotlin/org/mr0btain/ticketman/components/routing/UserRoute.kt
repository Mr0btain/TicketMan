package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.hashPassword
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
    // Sync between client and server
    authenticate {
        post("/sync") {
            val syncRequest = call.receive<SyncRequest>()
            val identifier = call.getIdentifierFromToken()

            database.all()?.let { data ->
                val user = data.firstOrNull { it.identifier == identifier }

                if (user != null) {
                    // Filter items by ones the client does not contain
                    val responseData = data.filter { !syncRequest.clientData.contains(it.identifier) }.map { it.copy(password = "") }

                    // Map all the ids in the local database
                    val dataIdentifiers = data.map { it.identifier }

                    // Filter response data so we can send back a response to request those that are not in our database
                    val requested = syncRequest.clientData.filter { !dataIdentifiers.contains(it) }

                    when (user.accessLevel) {
                        AccessLevel.ADMIN -> {
                            call.respond(HttpStatusCode.OK, SyncResponse(responseData, requested))
                        }

                        else -> {
                            val restrictedData = responseData.map { user ->
                                User(
                                    identifier = user.identifier,
                                    name = user.name
                                )
                            }

                            call.respond(HttpStatusCode.OK, SyncResponse(restrictedData, requested))
                        }
                    }
                } else call.respond(HttpStatusCode.NotFound)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // TODO: Not sure if this is the right way to go about this
    get("/isnewdatabase") {
        if (database.all()?.isEmpty() ?: true) call.respond(BooleanResponse(true))
        else call.respond(BooleanResponse(false))
    }

    post("/register/first") {
        val identifier = Uuid.generateV7()

        if (database.all()?.isEmpty() ?: true) {
            val userRequest = call.receive<UserRequest>()

            // TODO: Figure this out
            // First user is always admin?
            val user = userRequest.toModel(identifier).copy(accessLevel = AccessLevel.ADMIN)

            database.add(user)
            call.respond(HttpStatusCode.Created, user.toResponse())
        } else call.respond(HttpStatusCode.Unauthorized)
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
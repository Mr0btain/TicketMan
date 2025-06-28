package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.database.UserDatabase
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.hashPassword
import org.mr0btain.ticketman.server.request.UserRequest
import org.mr0btain.ticketman.server.response.BooleanResponse
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import org.mr0btain.ticketman.server.response.UserResponse
import java.util.UUID

/**
 * User routes
 */

fun Route.userRoute(
    database: UserDatabase
) {
    // TODO: Not sure if this is the right way to go about this
    get("/isnewdatabase") {
        if (database.getAll()?.isEmpty() ?: true) call.respond(BooleanResponse(true))
        else call.respond(BooleanResponse(false))
    }

    post("/register/first") {
        val identifier = database.getNextId()

        if (database.getAll()?.isEmpty() ?: true && identifier != null) {
            val userRequest = call.receive<UserRequest>()

            // First user is always admin
            val user = userRequest.toModel(identifier).copy(accessLevel = AccessLevel.ADMIN)

            if (database.add(user)) call.respond(HttpStatusCode.Created, user.toResponse())
            else call.respond(HttpStatusCode.BadRequest)
        } else call.respond(HttpStatusCode.Unauthorized)
    }

    authenticate("admin") {
        post("/register") {
            val userRequest = call.receive<UserRequest>()
            val identifier = database.getNextId()

            if (identifier != null) {
                val user = userRequest.toModel(identifier)

                if (!database.usernameAlreadyExists(user.username)) {
                    if (database.add(user)) call.respond(HttpStatusCode.Created, user.toResponse())
                    else call.respond(HttpStatusCode.BadRequest)
                } else call.respond(HttpStatusCode.Conflict)
            } else call.respond(HttpStatusCode.BadRequest)
        }
    }

    // Get all customers
    authenticate("restricted") {
        get("/all") {
            database.getAll()?.let { data ->
                call.respond(data)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Update Customer
    authenticate("admin") {
        post("/update") {
            try {
                val user = call.receive<User>()

                if (database.update(user)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Bulk add customers mostly for syncing
    authenticate("admin") {
        post("/add/all") {
            try {
                val customers = call.receive<List<User>>()

                if (database.addAll(customers)) call.respond(ServerResponse(ServerResult.SUCCESS))
                else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Delete customer
    authenticate("admin") {
        delete("/delete/{userIdentifier}") {
            try {
                val identifier = UUID.fromString(call.parameters["userIdentifier"])

                if (identifier == null) {
                    call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                    return@delete
                }

                if (database.remove(identifier)) call.respond(ServerResponse(ServerResult.SUCCESS))
                else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }
}

private fun UserRequest.toModel(id: UUID): User =
    User(
        identifier = id,
        username = username,
        password = hashPassword(password),
        name = name,
        notes = notes,
        contactInfo = contactInfo,
        tickets = tickets,
        accessLevel = accessLevel
    )

private fun User.toResponse(): UserResponse =
    UserResponse(
        identifier = identifier,
        accessLevel = accessLevel
    )
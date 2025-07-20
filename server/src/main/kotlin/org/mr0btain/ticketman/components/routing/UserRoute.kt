package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.hashPassword
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.database.UserLocalDatabaseClientImpl
import org.mr0btain.ticketman.server.request.UserRequest
import org.mr0btain.ticketman.server.response.BooleanResponse
import org.mr0btain.ticketman.server.response.UserResponse

/**
 * User routes
 */
// TODO: Figure out encryption

fun Route.userRoute(
    database: UserLocalDatabaseClientImpl,
    encryptionService: EncryptionService
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
                val userExists = database.getAll()?.map { it.username }?.contains(user.username)

                if (userExists != null && !userExists) {
                    if (database.add(user)) call.respond(HttpStatusCode.Created, user.toResponse())
                    else call.respond(HttpStatusCode.BadRequest)
                } else call.respond(HttpStatusCode.Conflict)
            } else call.respond(HttpStatusCode.BadRequest)
        }
    }

    // Get all customers
    authenticate("restricted") {
        get("/all") {
            call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim("identifier")
                ?.asString()
                ?.let { identifier ->
                    database.getById(identifier)?.let { user ->
                        when (user.accessLevel) {
                            AccessLevel.ADMIN -> call.respond(HttpStatusCode.Accepted, database.getAll() ?: emptyList())
                            else -> call.respond(
                                HttpStatusCode.Accepted,
                                database.getAll()?.map { item ->
                                    User(
                                        identifier = item.identifier,
                                        name = item.name,
                                        tickets = item.tickets,
                                        time = item.time
                                    )
                                } ?: emptyList()
                            )
                        }
                    } ?: call.respond(HttpStatusCode.NotFound)
            } ?: call.respond(HttpStatusCode.NotAcceptable)
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

                if (database.addAll(customers)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Delete customer
    authenticate("admin") {
        delete("/delete/{userIdentifier}") {
            try {
                val identifier = call.parameters["userIdentifier"]

                if (identifier == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }

                if (database.remove(identifier)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

private fun UserRequest.toModel(id: String): User =
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
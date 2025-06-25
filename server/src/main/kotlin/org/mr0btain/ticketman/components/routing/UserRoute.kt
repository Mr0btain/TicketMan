package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.employee.AccessLevel
import org.mr0btain.ticketman.database.LocalDatabase
import org.mr0btain.ticketman.database.UserDatabase
import org.mr0btain.ticketman.server.User
import org.mr0btain.ticketman.server.hashPassword
import org.mr0btain.ticketman.server.request.UserRequest
import org.mr0btain.ticketman.server.response.RegisterResponse
import org.mr0btain.ticketman.server.response.UserResponse
import java.util.UUID

/**
 * User routes
 */

fun Route.userRoute(
    userService: UserService,
    userDatabase: UserDatabase,
    isNewDatabase: Boolean
) {
    // TODO: Only allow admins to use this once the og admin has been added
    if (isNewDatabase) {
        post("/register") {
            val userRequest = call.receive<UserRequest>()

            val createdUser = userService.save(
                user = userRequest.toModel().copy(accessLevel = AccessLevel.ADMIN)
            ) ?: return@post call.respond(HttpStatusCode.BadRequest)

            call.respond(RegisterResponse(createdUser.identifier))
        }
    } else {
        authenticate("admin") {
            post("/register") {
                val userRequest = call.receive<UserRequest>()

                val createdUser = userService.save(
                    user = userRequest.toModel()
                ) ?: return@post call.respond(HttpStatusCode.BadRequest)

                call.respond(RegisterResponse(createdUser.identifier))
            }
        }
    }


    // TODO: Setup access level so only admins can get certain data
    authenticate {
        get("/getAll") {
            val users = userService.findAll()

            call.respond(
                message = users.map(User::toResponse)
            )
        }
    }

    authenticate {
        get("/{id}") {
            val id: String = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)

            val foundUser = userService.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(
                message = foundUser.toResponse()
            )
        }
    }
}

private fun UserRequest.toModel(): User =
    User(
        identifier = UUID.randomUUID(),
        username = username,
        password = hashPassword(password),
        accessLevel = accessLevel
    )

private fun User.toResponse(): UserResponse =
    UserResponse(
        identifier = identifier,
        username = username,
        password = password,
        accessLevel = accessLevel
    )

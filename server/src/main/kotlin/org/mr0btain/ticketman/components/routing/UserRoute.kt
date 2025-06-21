package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.server.User
import org.mr0btain.ticketman.server.request.UserRequest
import org.mr0btain.ticketman.server.response.UserResponse
import java.util.UUID

fun Route.userRoute(userService: UserService) {
    post {
        val userRequest = call.receive<UserRequest>()

        val createdUser = userService.save(
            user = userRequest.toModel()
        ) ?: return@post call.respond(HttpStatusCode.BadRequest)

        call.response.header(
            name = "id",
            value = createdUser.id.toString()
        )

        call.respond(HttpStatusCode.Created)
    }

    get {
        val users = userService.fundAll()

        call.respond(
            message = users.map(User::toResponse)
        )
    }

    get("/{id}") {
        val id: String = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)

        val foundUser = userService.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(
            message = foundUser.toResponse()
        )
    }
}

private fun UserRequest.toModel(): User =
    User(
        id = UUID.randomUUID(),
        username = this.username,
        password = this.password
    )

private fun User.toResponse(): UserResponse =
    UserResponse(
        id = id,
        username = username,
        password = password
    )

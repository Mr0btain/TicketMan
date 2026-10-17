package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.RefreshTokenResponse

/**
 * Authorization routes
 */

// TODO: These let make it a bit hard to follow. Maybe expand them to if statements to make it easier to understand
// TODO: Add logging
// Get rid of encryption service for now, redo it later. For the moment just get it working.

fun Route.authRoute(
    userService: UserService
) {
    post {
        // Receive data
        val received = call.receive<LoginRequest>()

        // Try to parse the message into a LoginRequest and authenticate the user, responding with
        // the uuid and tokens.
        userService.authenticate(received)?.let { authResponse ->
            call.respond(HttpStatusCode.Accepted, authResponse)
        } ?: call.respond(HttpStatusCode.Unauthorized) // Handle failed authentication
    }

    // This is essentially the login. This will authenticate then hit back with the access level
    post("/user") {
        // Receive request body and parse it into a LoginRequest
        val request = call.receive<LoginRequest>()

        userService.authenticateUser(request)?.let { user ->

            // Respond with HTTP 202 Accepted if authentication and key update are successful
            call.respond(HttpStatusCode.Accepted, user.accessLevel)

        } ?: call.respond(HttpStatusCode.Unauthorized) // Respond with 401 if authentication fails
    }

    // Ends the calling device's session
    authenticate("restricted") {
        post("/logout") {
            val sessionId = call.getSessionIdFromToken()

            if (sessionId != null && userService.logout(sessionId)) {
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // Get Tokens
    post("/refresh") {
        // Receive refresh token request
        val request = call.receive<RefreshTokenRequest>()

        // Try to refresh the token using the provided refresh token
        userService.refreshToken(request.token)?.let { newToken ->
            call.respond(HttpStatusCode.Accepted, RefreshTokenResponse(newToken))
        } ?: call.respond(HttpStatusCode.Unauthorized)
    }
}
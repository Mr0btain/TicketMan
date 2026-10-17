package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.components.service.LoginResult
import org.mr0btain.ticketman.components.service.RequestLimiter
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginStatus
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import kotlin.time.Duration.Companion.hours

/**
 * Authorization routes
 */

// TODO: Add logging
// Get rid of encryption service for now, redo it later. For the moment just get it working.

fun Route.authRoute(
    userService: UserService,
    accountService: AccountService
) {
    // Anyone can ask for an account, so cap how often one address can
    val requestLimiter = RequestLimiter(maxAttempts = 5, window = 1.hours)

    post {
        val received = call.receive<LoginRequest>()

        when (val result = userService.authenticate(received)) {
            is LoginResult.Success -> call.respond(HttpStatusCode.Accepted, result.response)
            is LoginResult.InviteSetup -> call.respond(HttpStatusCode.Accepted, result.response)

            // No tokens, just lets the screen say what's going on
            LoginResult.PendingApproval -> call.respond(
                HttpStatusCode.Accepted,
                AuthResponse(id = null, accessToken = null, refreshToken = null, status = LoginStatus.PENDING_APPROVAL)
            )

            LoginResult.Failed -> call.respond(HttpStatusCode.Unauthorized)
        }
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

    // Asking for an account from the login screen, it waits for an admin
    post("/request-account") {
        if (!requestLimiter.tryAcquire(call.request.origin.remoteHost)) {
            call.respond(HttpStatusCode.TooManyRequests, "Too many requests, try again later")
            return@post
        }

        when (val result = accountService.requestAccount(call.receive<AccountRequest>())) {
            is AccountResult.Done -> call.respond(HttpStatusCode.Accepted)
            is AccountResult.Invalid -> call.respond(HttpStatusCode.BadRequest, result.message)
            is AccountResult.Conflict -> call.respond(HttpStatusCode.Conflict, result.message)
            AccountResult.NotFound -> call.respond(HttpStatusCode.NotFound)
        }
    }

    // Finishing an invite with the setup token from logging in with the code
    post("/setup/invite") {
        when (val result = accountService.completeInviteSetup(call.receive<InviteSetupRequest>())) {
            is AccountResult.Done -> call.respond(HttpStatusCode.Accepted, result.value)
            is AccountResult.Invalid -> call.respond(HttpStatusCode.BadRequest, result.message)
            is AccountResult.Conflict -> call.respond(HttpStatusCode.Conflict, result.message)
            AccountResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Invite not found")
        }
    }

    // Ends the calling device's session, works mid setup too
    authenticate("setup") {
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

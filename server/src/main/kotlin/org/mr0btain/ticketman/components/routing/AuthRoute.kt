package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.StationDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.components.service.LoginResult
import org.mr0btain.ticketman.components.service.PinLoginResult
import org.mr0btain.ticketman.components.service.RequestLimiter
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PinLoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginStatus
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Authorization routes
 */

// TODO: Add logging

fun Route.authRoute(
    userService: UserService,
    accountService: AccountService,
    stationDatabase: StationDatabase,
    userDatabase: UserDatabase
) {
    // Anyone can ask for an account, so cap how often one address can
    val requestLimiter = RequestLimiter(maxAttempts = 5, window = 1.hours)
    // Wrong passwords are capped per address, and per username so many addresses can't share the guessing
    val loginLimiter = LoginLimiter()

    post {
        val received = call.receive<LoginRequest>()

        if (loginLimiter.refuse(call, received.username)) return@post

        when (val result = userService.authenticate(received)) {
            is LoginResult.Success -> call.respond(HttpStatusCode.Accepted, result.response)
            is LoginResult.InviteSetup -> call.respond(HttpStatusCode.Accepted, result.response)

            // No tokens, just lets the screen say what's going on
            LoginResult.PendingApproval -> call.respond(
                HttpStatusCode.Accepted,
                AuthResponse(id = null, accessToken = null, refreshToken = null, status = LoginStatus.PENDING_APPROVAL)
            )

            LoginResult.Failed -> {
                loginLimiter.failed(call, received.username)
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }

    // This is essentially the login. This will authenticate then hit back with the access level
    post("/user") {
        // Receive request body and parse it into a LoginRequest
        val request = call.receive<LoginRequest>()

        if (loginLimiter.refuse(call, request.username)) return@post

        userService.authenticateUser(request)?.let { user ->

            // Respond with HTTP 202 Accepted if authentication and key update are successful
            call.respond(HttpStatusCode.Accepted, user.accessLevel)

        } ?: run {
            // Respond with 401 if authentication fails
            loginLimiter.failed(call, request.username)
            call.respond(HttpStatusCode.Unauthorized)
        }
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

    // ------------------------------------------ Stations ------------------------------------------
    // These prove who they are with the station's secret rather than a login

    // Switch to someone at a station with their PIN
    post("/pin") {
        when (val result = userService.pinLogin(call.receive<PinLoginRequest>())) {
            is PinLoginResult.Success -> call.respond(HttpStatusCode.Accepted, result.response)
            PinLoginResult.BadStation -> call.respond(HttpStatusCode.Forbidden, "This device isn't a registered station")
            PinLoginResult.StationNotApproved -> call.respond(HttpStatusCode.Forbidden, "This station is waiting for an admin to approve it")
            PinLoginResult.Locked -> call.respond(HttpStatusCode.Locked, "PIN is locked, ask an admin to unlock it")
            is PinLoginResult.Wrong -> call.respond(
                HttpStatusCode.Unauthorized,
                result.attemptsLeft?.let { "Wrong PIN, $it tries left" } ?: "Wrong PIN"
            )
        }
    }

    // Station's own record, so it can see whether it's been approved
    post("/station/status") {
        val station = stationDatabase.verify(call.receive<StationCredentials>())

        if (station != null) call.respond(HttpStatusCode.OK, station) else call.respond(HttpStatusCode.NotFound)
    }

    // Who can PIN in, for the station's sign in screen
    post("/station/staff") {
        val station = stationDatabase.verify(call.receive<StationCredentials>())

        when {
            station == null -> call.respond(HttpStatusCode.Forbidden, "This device isn't a registered station")
            station.status != StationStatus.APPROVED -> call.respond(HttpStatusCode.Forbidden, "This station is waiting for an admin to approve it")
            else -> call.respond(
                HttpStatusCode.OK,
                userDatabase.getPinUsers().map { StaffMember(it.identifier, it.username, it.name) }
            )
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

/**
 * Caps wrong password logins. Only failures count, so staff logging in all day never hit it.
 * A limited username is refused even with the right password until its window passes
 */
private class LoginLimiter {
    private val byAddress = RequestLimiter(maxAttempts = 20, window = 15.minutes)
    private val byUsername = RequestLimiter(maxAttempts = 10, window = 15.minutes)

    /** Answers 429 and returns true when this address or username has had too many wrong tries */
    suspend fun refuse(call: ApplicationCall, username: String): Boolean {
        if (!byAddress.isLimited(call.address()) && !byUsername.isLimited(username.key())) return false

        call.respond(HttpStatusCode.TooManyRequests, "Too many wrong logins, try again in a few minutes")
        return true
    }

    fun failed(call: ApplicationCall, username: String) {
        byAddress.record(call.address())
        byUsername.record(username.key())
    }

    private fun ApplicationCall.address() = request.origin.remoteHost
    private fun String.key() = trim().lowercase()
}

package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.AuthPaths
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.StationDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.components.service.LoginLimiter
import org.mr0btain.ticketman.components.service.LoginResult
import org.mr0btain.ticketman.components.service.PinLoginResult
import org.mr0btain.ticketman.components.service.RequestLimiter
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.audit.AuditAction
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
import kotlin.time.Duration.Companion.hours

/**
 * Authorization routes
 */

fun Route.authRoute(
    userService: UserService,
    accountService: AccountService,
    stationDatabase: StationDatabase,
    userDatabase: UserDatabase,
    auditLog: AuditLogDatabase,
    loginLimiter: LoginLimiter
) {
    // Anyone can ask for an account, so cap how often one address can
    val requestLimiter = RequestLimiter(maxAttempts = 5, window = 1.hours)

    post {
        val received = call.receive<LoginRequest>()
        val address = call.request.origin.remoteHost

        // Wrong passwords are capped per address, per username, and across the server, see LoginLimiter
        loginLimiter.waitFor(address, received.username)?.let { wait ->
            val seconds = (wait + 999) / 1000
            call.response.header(HttpHeaders.RetryAfter, seconds.toString())
            call.respond(HttpStatusCode.TooManyRequests, "Too many wrong logins, try again in ${waitText(seconds)}")
            return@post
        }

        when (val result = userService.authenticate(received)) {
            is LoginResult.Success -> {
                loginLimiter.succeeded(received.username)
                call.respond(HttpStatusCode.Accepted, result.response)
            }
            is LoginResult.InviteSetup -> call.respond(HttpStatusCode.Accepted, result.response)

            // No tokens, just lets the screen say what's going on
            LoginResult.PendingApproval -> call.respond(
                HttpStatusCode.Accepted,
                AuthResponse(id = null, accessToken = null, refreshToken = null, status = LoginStatus.PENDING_APPROVAL)
            )

            LoginResult.Failed -> {
                loginLimiter.failed(address, received.username)
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }

    // Asking for an account from the login screen, it waits for an admin
    post(AuthPaths.REQUEST_ACCOUNT) {
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
    post(AuthPaths.SETUP_INVITE) {
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
    post(AuthPaths.PIN) {
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
    post(AuthPaths.STATION_STATUS) {
        val station = stationDatabase.verify(call.receive<StationCredentials>())

        if (station != null) call.respond(HttpStatusCode.OK, station) else call.respond(HttpStatusCode.NotFound)
    }

    // Who can PIN in, for the station's sign in screen
    post(AuthPaths.STATION_STAFF) {
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
        post(AuthPaths.LOGOUT) {
            val sessionId = call.getSessionIdFromToken()

            if (sessionId != null && userService.logout(sessionId)) {
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // Get Tokens
    post(AuthPaths.REFRESH) {
        // Receive refresh token request
        val request = call.receive<RefreshTokenRequest>()

        // Try to refresh the token using the provided refresh token, which gets swapped for a new one
        userService.refreshToken(request.token)?.let { response ->
            call.respond(HttpStatusCode.Accepted, response)
        } ?: call.respond(HttpStatusCode.Unauthorized)
    }

    // An admin left kiosk mode on a station. Their PIN session there stops ending with the kiosk's idle lock
    authenticate("admin") {
        post(AuthPaths.KIOSK_LEAVE) {
            val sessionId = call.getSessionIdFromToken()
            val station = sessionId?.let { userService.leaveKiosk(it) }

            if (station == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.audit(auditLog, AuditAction.UPDATE, "station", details = "Left kiosk mode at $station")
            call.respond(HttpStatusCode.Accepted)
        }
    }
}

/** "40 seconds", "2 minutes" */
fun waitText(seconds: Long): String = when {
    seconds < 60 -> "$seconds second${if (seconds == 1L) "" else "s"}"
    else -> ((seconds + 59) / 60).let { "$it minute${if (it == 1L) "" else "s"}" }
}

package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.TokenResponse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Route.authRoute(jwtService: JwtService) {
    post {
        val loginRequest = call.receive<LoginRequest>()
        val expiresAt = Clock.System.now().plus(30.days)

        val token = jwtService.createJwtToken(loginRequest, expiresAt)

        token?.let {
            call.respond(
                TokenResponse(
                    username = loginRequest.username,
                    token = token,
                    expiresAt = expiresAt.toString()
                )
            )
        }  ?: call.respond(HttpStatusCode.Unauthorized)
    }
}
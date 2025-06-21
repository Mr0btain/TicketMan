package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.server.request.LoginRequest

fun Route.authRoute(jwtService: JwtService) {
    post {
        val loginRequest = call.receive<LoginRequest>()

        val token = jwtService.createJwtToken(loginRequest)

        token?.let {
            call.respond(hashMapOf("token" to it))
        }  ?: call.respond(HttpStatusCode.Unauthorized)
    }
}
package org.mr0btain.ticketman.components.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import org.mr0btain.ticketman.components.service.JwtService

fun Application.configureSecurity(
    jwtService: JwtService
) {
    authentication {
        jwt {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential)
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Bad Token")
            }
        }

        jwt("another-auth") {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential)
            }
        }
    }
}
package org.mr0btain.ticketman.components.plugins

import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import org.mr0btain.ticketman.components.service.JwtService

fun Application.configureSecurity(
    jwtService: JwtService
) {
    authentication {
        jwt {
            realm = jwtService.realm ?: ""
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential)
            }
        }

        jwt("another-auth") {
            realm = jwtService.realm ?: ""
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential)
            }
        }
    }
}
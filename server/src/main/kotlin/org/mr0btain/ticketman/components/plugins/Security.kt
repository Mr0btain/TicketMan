package org.mr0btain.ticketman.components.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.data.employee.AccessLevel
import org.mr0btain.ticketman.database.UserDatabase

/**
 * Security configuration for authentication
 */

fun Application.configureSecurity(
    jwtService: JwtService
) {
    authentication {
        jwt {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential, AccessLevel.NORMAL)
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Bad Token or Unauthorized")
            }
        }

        jwt("restricted") {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential, AccessLevel.RESTRICTED)
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Bad Token or Unauthorized")
            }
        }

        jwt("admin") {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.customValidator(credential, AccessLevel.ADMIN)
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Bad Token or Unauthorized")
            }
        }
    }
}
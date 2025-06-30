package org.mr0btain.ticketman.components.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.data.inventory.AccessLevel

/**
 * Security configuration for authentication
 */

fun Application.configureSecurity(
    jwtService: JwtService
) {
    authentication {
        // NORMAL access level
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

        // RESTRICTED access level
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

        // ADMIN Access level
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
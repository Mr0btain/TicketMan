package org.mr0btain.ticketman.components.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.JwtService.AccessCheck
import org.mr0btain.ticketman.data.user.AccessLevel

/** Set when a good token's user isn't allowed on the route, so the challenge answers 403 rather than 401 */
private val AccessDeniedKey = AttributeKey<Boolean>("AccessDenied")

const val NO_ACCESS_MESSAGE = "You don't have access to that"
const val BAD_TOKEN_MESSAGE = "Bad Token or Unauthorized"

/**
 * Security configuration for authentication. A missing, expired or revoked token gets a 401, which the client
 * answers by refreshing. A good token without the access the route needs gets a 403, since refreshing won't help
 */
fun Application.configureSecurity(
    jwtService: JwtService
) {
    // Lets the token in, or notes whether it was turned away for its access level
    fun ApplicationCall.admit(check: AccessCheck): JWTPrincipal? = when (check) {
        is AccessCheck.Allowed -> check.principal
        AccessCheck.Denied -> null.also { attributes.put(AccessDeniedKey, true) }
        AccessCheck.Invalid -> null
    }

    suspend fun ApplicationCall.refuse() {
        if (attributes.getOrNull(AccessDeniedKey) == true) {
            respond(HttpStatusCode.Forbidden, NO_ACCESS_MESSAGE)
        } else {
            respond(HttpStatusCode.Unauthorized, BAD_TOKEN_MESSAGE)
        }
    }

    authentication {
        // NORMAL access level
        jwt {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                admit(jwtService.checkAccess(credential, AccessLevel.NORMAL))
            }

            challenge { _, _ -> call.refuse() }
        }

        // RESTRICTED access level
        jwt("restricted") {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                admit(jwtService.checkAccess(credential, AccessLevel.RESTRICTED))
            }

            challenge { _, _ -> call.refuse() }
        }

        // Any active user, including ones still doing first login setup
        jwt("setup") {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                jwtService.setupValidator(credential)
            }

            challenge { _, _ -> call.refuse() }
        }

        // ADMIN Access level
        jwt("admin") {
            realm = jwtService.realm
            verifier(jwtService.jwtVerifier)

            validate { credential ->
                admit(jwtService.checkAccess(credential, AccessLevel.ADMIN))
            }

            challenge { _, _ -> call.refuse() }
        }
    }
}

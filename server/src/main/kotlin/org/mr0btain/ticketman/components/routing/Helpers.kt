package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

fun RoutingCall.getIdentifierFromToken(): Uuid? {
    val principal = principal<JWTPrincipal>()
    return principal?.getClaim("identifier", String::class)
        ?.let { Uuid.parse(it) }
}



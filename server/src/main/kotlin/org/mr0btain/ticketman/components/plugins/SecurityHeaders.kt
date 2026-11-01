package org.mr0btain.ticketman.components.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import org.mr0btain.ticketman.server.ApiRoutes

/**
 * Headers for the web client and the API: no other site can put them in a frame, a response is only ever what it says
 * it is, and addresses don't leak to other sites. The policy leaves scripts alone, the Wasm client has its own needs and
 * draws to a canvas rather than building pages from what it's sent. The status page sets its own, stricter ones
 */
fun Application.configureSecurityHeaders() {
    install(SecurityHeaders)
}

private val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    onCall { call ->
        val path = call.request.path()
        if (path == ApiRoutes.STATUS || path.startsWith("${ApiRoutes.STATUS}/")) return@onCall

        call.response.headers.apply {
            append("X-Content-Type-Options", "nosniff")
            append("X-Frame-Options", "DENY")
            append("Referrer-Policy", "no-referrer")
            append("Content-Security-Policy", "frame-ancestors 'none'; base-uri 'self'; object-src 'none'; form-action 'self'")
        }
    }
}

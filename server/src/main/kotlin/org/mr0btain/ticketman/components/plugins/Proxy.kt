package org.mr0btain.ticketman.components.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.forwardedheaders.*
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Takes the client's address and scheme from `X-Forwarded-*` headers when `TRUST_PROXY` is on, so login limits
 * count each device rather than the proxy. Only turn it on behind a reverse proxy: without one, anyone can send
 * the header. The last address in the header is the one the proxy added, earlier ones come from the client
 */
fun Application.configureProxy(trustProxy: Boolean) {
    if (!trustProxy) return

    install(XForwardedHeaders) {
        useLastProxy()
    }

    AppLogger.m("SERVER:Proxy", "Trusting X-Forwarded-* headers from a reverse proxy")
}

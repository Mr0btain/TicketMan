package org.mr0btain.ticketman.components.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.httpsredirect.*
import org.mr0btain.ticketman.components.tls.HttpMode
import org.mr0btain.ticketman.components.tls.TlsSettings

/**
 * Sends plain HTTP requests to the HTTPS port when `HTTP_MODE` is `redirect`. Temporary redirects, so turning it
 * back to `on` works straight away. Devices should be moved to an `https://` address first, since a redirected
 * POST arrives as a GET. Never behind a reverse proxy: requests reach the server as plain HTTP from the proxy, and
 * sending people to the server's own HTTPS port would go around it
 * @param proxy `PROXY`
 */
fun Application.configureHttps(settings: TlsSettings, proxy: Boolean) {
    if (proxy || !settings.enabled || settings.httpMode != HttpMode.REDIRECT) return

    install(HttpsRedirect) {
        sslPort = settings.httpsPort
        permanentRedirect = false
    }
}

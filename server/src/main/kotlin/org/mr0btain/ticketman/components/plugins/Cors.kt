package org.mr0btain.ticketman.components.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import org.mr0btain.ticketman.server.API_VERSION_HEADER
import org.mr0btain.ticketman.server.APP_VERSION_HEADER

/**
 * Lets the web client's dev server (`wasmJsBrowserDevelopmentRun`, on its own port) call this server. Only in
 * development mode: a real server serves the web client itself, on the same address, so it never needs this
 * @param hosts `CORS_HOSTS`, comma separated like `localhost:8080`. Blank allows any
 */
fun Application.configureCors(hosts: String) {
    if (!developmentMode) return

    install(CORS) {
        val allowed = hosts.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (allowed.isEmpty()) anyHost() else allowed.forEach { allowHost(it, schemes = listOf("http", "https")) }

        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(APP_VERSION_HEADER)
        allowNonSimpleContentTypes = true

        // Downloads read their file name from it
        exposeHeader(HttpHeaders.ContentDisposition)
        // The ping's versions, which the client checks against its own. It sends its own on every call
        exposeHeader(API_VERSION_HEADER)
        exposeHeader(APP_VERSION_HEADER)
        maxAgeInSeconds = 600
    }
}

package org.mr0btain.ticketman.components.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

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
        allowNonSimpleContentTypes = true

        // Downloads read their file name from it
        exposeHeader(HttpHeaders.ContentDisposition)
        maxAgeInSeconds = 600
    }
}

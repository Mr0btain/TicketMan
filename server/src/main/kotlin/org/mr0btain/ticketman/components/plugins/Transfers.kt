package org.mr0btain.ticketman.components.plugins

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*

/**
 * For files sent with respondFile: Range requests so big downloads can resume, and If-None-Match / ETag
 * handling so cached photos come back as a 304 with no body
 */
fun Application.configureTransfers() {
    install(PartialContent)
    // Before ConditionalHeaders, so it checks the versions this leaves
    install(EtagOverLastModified)
    install(ConditionalHeaders)
}

/**
 * Content with its own ETag (the web client's files) is checked by that alone. Files from a jar are all dated 1980,
 * the same in every build, and ConditionalHeaders answers 304 to a matching If-Modified-Since whatever the ETag
 * says, so a cache (like Cloudflare's) kept the old composeApp.js after an update, which asks for Wasm files the new
 * server doesn't have
 */
private val EtagOverLastModified = createApplicationPlugin("EtagOverLastModified") {
    on(ResponseBodyReadyForSend) { _, content ->
        if (content.versions.any { it is EntityTagVersion }) {
            content.versions = content.versions.filterNot { it is LastModifiedVersion }
        }
    }
}

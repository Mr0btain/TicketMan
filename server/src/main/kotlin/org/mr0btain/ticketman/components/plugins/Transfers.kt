package org.mr0btain.ticketman.components.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*

/**
 * For files sent with respondFile: Range requests so big downloads can resume, and If-None-Match / ETag
 * handling so cached photos come back as a 304 with no body
 */
fun Application.configureTransfers() {
    install(PartialContent)
    install(ConditionalHeaders)
}

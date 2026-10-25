package org.mr0btain.ticketman.components.network.user

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.sendAppVersion

/** @param onVersionRejected The server turned a request away for this app's version, see [sendAppVersion] */
fun createUserHttpClient(
    engine: HttpClientEngine,
    onVersionRejected: () -> Unit = {}
): HttpClient {
    return HttpClient(engine) {
        // A server with HTTP_MODE=redirect answers plain HTTP with a redirect, which ServerUserClient reads to move
        // to its https:// address. Followed, it would land on a certificate this device may not trust yet
        followRedirects = false

        // Not bodies, these carry passwords and PINs
        install(Logging) {
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        install(ContentNegotiation) {
            json(json = Json { ignoreUnknownKeys = true })
        }

        sendAppVersion(onVersionRejected)
    }
}
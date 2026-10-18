package org.mr0btain.ticketman.components.network.user

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createUserHttpClient(
    engine: HttpClientEngine
): HttpClient {
    // TODO: Secure the storage of these, maybe encrypt?
    return HttpClient(engine) {
        // Not bodies, these carry passwords and PINs
        install(Logging) {
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        install(ContentNegotiation) {
            json(json = Json { ignoreUnknownKeys = true })
        }
    }
}
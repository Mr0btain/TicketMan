package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

fun createUserHttpClient(
    engine: HttpClientEngine
): HttpClient {
    // TODO: Secure the storage of these, maybe encrypt?
    return HttpClient(engine) {
        // Logging
        install(Logging) { level = LogLevel.ALL }

        install(ContentNegotiation) {
            json(json = Json { ignoreUnknownKeys = true })
        }
    }
}
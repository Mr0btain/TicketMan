@file:Suppress("LocalVariableName")

package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.ServerHandler
import org.mr0btain.ticketman.logging.AppLogger

fun createDatabaseHttpClient(
    engine: HttpClientEngine,
    serverHandler: ServerHandler
): HttpClient {
    val TAG = "CreateDatabaseHttpClient"

    // TODO: Secure the storage of these, maybe encrypt?
    return HttpClient(engine) {
        // Logging
        install(Logging) { level = LogLevel.ALL }

        install(ContentNegotiation) {
            json(json = Json { ignoreUnknownKeys = true })
        }

        install(Auth) {
            bearer {
                loadTokens {
                    AppLogger.m("$TAG:LoadTokens", "Loading stored tokens")
                    // Load tokens from a local storage and return them as the 'BearerTokens' instance
                    val accessToken = serverHandler.getAccessToken() ?: ""
                    val refreshToken = serverHandler.getRefreshToken() ?: ""

                    BearerTokens(accessToken, refreshToken)
                }

                refreshTokens {
                    AppLogger.m("$TAG:RefreshTokens", "Refreshing tokens with server")
                    // Refresh tokens and return them as the 'BearerTokens' instance
                    val authResponse = serverHandler.refreshTokens()

                    if (authResponse.isNotBlank()) {
                        AppLogger.m("$TAG:RefreshTokens", "SUCCESS")
                        BearerTokens("", "")
                        //BearerTokens(authResponse.accessToken ?: "", authResponse.refreshToken)
                    } else {
                        AppLogger.i("$TAG:RefreshTokens", "FAIL")
                        BearerTokens("", "")
                    }
                }
            }
        }
    }
}
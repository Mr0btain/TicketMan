@file:Suppress("LocalVariableName")

package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.token.Token
import org.mr0btain.ticketman.logging.AppLogger

fun createRemoteHttpClient(
    engine: HttpClientEngine,
    getAccessToken: () -> String?,
    getRefreshToken: () -> String?,
    refreshTokens: suspend () -> Token?
): HttpClient {
    val TAG = "CreateRemoteHttpClient"

    // TODO: Secure the storage of these, maybe encrypt?
    return HttpClient(engine) {
        // Headers only: logging bodies makes Ktor hold every photo and backup in memory, and dumps them to the log
        install(Logging) {
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        // Ordinary calls give up after a minute, big transfers turn this off per request with [transferTimeout]
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }

        install(ContentNegotiation) {
            json(json = Json { ignoreUnknownKeys = true })
        }

        install(Auth) {
            bearer {
                loadTokens {
                    AppLogger.m("$TAG:LoadTokens", "Loading stored tokens")
                    // Load tokens from a local storage and return them as the 'BearerTokens' instance
                    val accessToken = getAccessToken() ?: ""
                    val refreshToken = getRefreshToken() ?: ""

                    BearerTokens(accessToken, refreshToken)
                }

                refreshTokens {
                    AppLogger.m("$TAG:RefreshTokens", "Refreshing tokens with server")
                    // Refresh tokens and return them as the 'BearerTokens' instance
                    val token = refreshTokens()

                    if (token != null) {
                        AppLogger.m("$TAG:RefreshTokens", "SUCCESS")
                        BearerTokens(token.access, token.refresh)
                    } else {
                        // Returning null stops Ktor from retrying so the 401 reaches the caller
                        AppLogger.i("$TAG:RefreshTokens", "FAIL")
                        null
                    }
                }
            }
        }
    }
}

private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val REQUEST_TIMEOUT_MILLIS = 60_000L

/** Longest a big transfer may sit with no bytes moving before it's dropped */
private const val TRANSFER_SOCKET_TIMEOUT_MILLIS = 120_000L

/**
 * For uploads and downloads that can take minutes: no overall limit, but a stalled connection still fails
 */
fun HttpRequestBuilder.transferTimeout() {
    timeout {
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = TRANSFER_SOCKET_TIMEOUT_MILLIS
    }
}

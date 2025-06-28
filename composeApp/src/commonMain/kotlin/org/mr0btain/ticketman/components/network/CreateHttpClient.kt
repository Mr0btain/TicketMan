package org.mr0btain.ticketman.components.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

fun createHttpClient(
    engine: HttpClientEngine,
    mainViewModel: MainViewModel
): HttpClient {
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
                    println("Loading tokens")
                    // Load tokens from a local storage and return them as the 'BearerTokens' instance
                    val accessToken = mainViewModel.getAccessToken()
                    val refreshToken = mainViewModel.getRefreshToken()

                    BearerTokens(accessToken, refreshToken)
                }

                refreshTokens { // this: RefreshTokensParams
                    println("Refreshing tokens bearer")
                    // Refresh tokens and return them as the 'BearerTokens' instance
                    val authResponse = mainViewModel.refreshTokens()

                    if (authResponse != null) {
                        BearerTokens(authResponse.accessToken, authResponse.refreshToken)
                    } else {
                        mainViewModel
                        BearerTokens("", "")
                    }
                }
            }
        }
    }
}
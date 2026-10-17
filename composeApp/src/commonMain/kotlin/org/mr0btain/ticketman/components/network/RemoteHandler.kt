package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.database.*
import org.mr0btain.ticketman.components.network.user.ServerUserClient
import org.mr0btain.ticketman.components.network.user.createUserHttpClient
import org.mr0btain.ticketman.data.token.Token
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult

/**
 * Handles the interaction and states of server side stuff
 */
class RemoteHandler(
    engine: HttpClientEngine,
    private val localStorage: LocalStorage
) {
    private val TAG = "ServerHandler"
    private val SET_SERVER_URL_TAG = "$TAG:SetServerUrl"
    private val REFRESH_TOKENS_TAG = "$TAG:RefreshTokens"
    private val LOGIN_TAG = "$TAG:Login"
    private val LOGOUT_TAG = "$TAG:Logout"
    val userClient = ServerUserClient(createUserHttpClient(engine), localStorage)

    private val httpClient = createRemoteHttpClient(
        engine = engine,
        getAccessToken = { getAccessToken() },
        getRefreshToken = { getRefreshToken() },
        refreshTokens = { refreshTokens() }
    )

    // Clients used to interact with server. Only real difference is the types and server urls
    // Could possibly combine this into one client somehow. just supplying the url for each call?
    val customerRepo = CustomerRemoteRepositoryImpl(httpClient)
    val ticketRepo = TicketRemoteRepositoryImpl(httpClient)
    val inventoryRepo = InventoryRemoteRepositoryImpl(httpClient)
    val userRepo = UserRemoteRepositoryImpl(httpClient)
    val adminRepo = AdminRemoteRepository(httpClient)

    // Emits when the session can't be refreshed and the user needs to log in again
    private val _authExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authExpired = _authExpired.asSharedFlow()

    /**
     * Sets url to the server in all clients and tries to log in with the stored credentials
     */
    suspend fun setUrl(url: String): ConnectionResult {
        if (url.isNotBlank()) {
            // Check if we can establish a connection with this address, if success then set it.
            if (userClient.checkServerConnection(url)) {
                userClient.changeServerUrl(url)
                customerRepo.serverUrl = url
                ticketRepo.serverUrl = url
                inventoryRepo.serverUrl = url
                userRepo.serverUrl = url
                adminRepo.serverUrl = url

                AppLogger.m(SET_SERVER_URL_TAG, SimpleResult.SUCCESS)

                // Log in with stored credentials, then drop the tokens the http client cached
                // before login so the next request loads the new ones
                return if (userClient.login()) {
                    onLoggedIn()
                    ConnectionResult.LOGGED_IN
                } else {
                    AppLogger.i(SET_SERVER_URL_TAG, "Connected but login failed")
                    ConnectionResult.CONNECTED
                }
            }
        } else {
            AppLogger.m(SET_SERVER_URL_TAG, "FAIL: Url is empty")
        }

        return ConnectionResult.UNREACHABLE
    }

    /**
     * Gets stored access token from database
     */
    fun getAccessToken() = localStorage.accessToken

    /**
     * Gets both stored tokens from database
     */
    fun getRefreshToken() = localStorage.refreshToken

    /**
     * Gets both stored tokens from database
     */
    suspend fun setUser(user: User) =  "" //dataHandler.userDataHandler.setCurrent(user)

    /**
     * Refreshes tokens for authentication. Falls back to logging in with the stored credentials
     * if there is no refresh token or the server rejects it.
     * @return The new tokens, or null if we couldn't authenticate
     */
    suspend fun refreshTokens(): Token? {
        if (localStorage.refreshToken.isNotBlank()) {
            // On success this stores the new access token through updateAuthTokens
            if (userClient.refreshTokens(localStorage.refreshToken) is Result.Success) {
                AppLogger.m(REFRESH_TOKENS_TAG, SimpleResult.SUCCESS)
                return getStoredToken()
            }

            AppLogger.i(REFRESH_TOKENS_TAG, "Refresh token rejected, logging in again")
        }

        return if (userClient.login()) {
            AppLogger.m(REFRESH_TOKENS_TAG, SimpleResult.SUCCESS)
            getStoredToken()
        } else {
            AppLogger.i(REFRESH_TOKENS_TAG, SimpleResult.FAIL)

            // Drop the dead tokens and let whoever is listening know we need a fresh login
            localStorage.clearSession()
            _authExpired.tryEmit(Unit)
            null
        }
    }

    // Identifier isn't used by the http client so leave it blank
    private fun getStoredToken() = Token(
        identifier = "",
        refresh = localStorage.refreshToken,
        access = localStorage.accessToken
    )

    /**
     * Try to log in to server with username and password
     */
    suspend fun login(user: String, pass: String): Boolean {
        val result = userClient.login(user, pass)

        if (result) {
            onLoggedIn()
            AppLogger.m(LOGIN_TAG, SimpleResult.SUCCESS)
        } else {
            AppLogger.i(LOGIN_TAG, SimpleResult.FAIL)
        }

        return result
    }

    /**
     * Drops the current session. The saved password is cleared too so the refresh fallback
     * doesn't log us straight back in, the username is kept for prefilling the login form
     */
    suspend fun logout() {
        localStorage.clearSession()
        localStorage.password = ""
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        AppLogger.m(LOGOUT_TAG, SimpleResult.SUCCESS)
    }

    /**
     * Drop the tokens the http client cached before login so the next request loads the new ones
     */
    private fun onLoggedIn() {
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
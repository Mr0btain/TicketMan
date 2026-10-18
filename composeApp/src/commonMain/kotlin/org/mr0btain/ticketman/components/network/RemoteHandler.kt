package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.database.*
import org.mr0btain.ticketman.components.network.user.LoginOutcome
import org.mr0btain.ticketman.components.network.user.ServerUserClient
import org.mr0btain.ticketman.components.network.image.ImageRemoteRepository
import org.mr0btain.ticketman.components.network.user.createUserHttpClient
import org.mr0btain.ticketman.data.token.Token
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import kotlin.uuid.Uuid
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
    val counterRepo = CounterRemoteRepository(httpClient)
    val invoiceRepo = InvoiceRemoteRepository(httpClient)
    val imageRepo = ImageRemoteRepository(httpClient)

    /**
     * Whether an expired session can be fixed by logging in again with the saved username and password.
     * Kiosks turn this off so a locked counter can't quietly sign the device's owner back in
     */
    var credentialFallback: Boolean = true

    // Base url once a server has been reached, used for routes outside the repositories
    private var serverUrl = ""

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
                serverUrl = url.trimEnd('/')
                customerRepo.serverUrl = url
                ticketRepo.serverUrl = url
                inventoryRepo.serverUrl = url
                userRepo.serverUrl = url
                adminRepo.serverUrl = url
                counterRepo.serverUrl = url
                invoiceRepo.serverUrl = url
                imageRepo.serverUrl = url

                AppLogger.m(SET_SERVER_URL_TAG, SimpleResult.SUCCESS)

                // Log in with stored credentials, then drop the tokens the http client cached
                // before login so the next request loads the new ones
                return if (userClient.login() is LoginOutcome.LoggedIn) {
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
     * Session this device is logged in with, read from the access token's sid claim. Only used to label things
     * on screen, the server does the real checks
     */
    fun currentSessionId(): String? {
        val payload = localStorage.accessToken.split(".").getOrNull(1) ?: return null

        return try {
            val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
            Json.parseToJsonElement(decoded).jsonObject["sid"]?.jsonPrimitive?.content
        } catch (ex: Exception) {
            null
        }
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

        return if (credentialFallback && userClient.login() is LoginOutcome.LoggedIn) {
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
    suspend fun login(user: String, pass: String): LoginOutcome {
        val result = userClient.login(user, pass)

        if (result is LoginOutcome.LoggedIn) {
            onLoggedIn()
            AppLogger.m(LOGIN_TAG, SimpleResult.SUCCESS)
        } else {
            AppLogger.i(LOGIN_TAG, result.toString())
        }

        return result
    }

    /**
     * Switches who's signed in at this station. The previous person's session on this station ends
     */
    suspend fun pinLogin(userIdentifier: Uuid, pin: String): EmptyResult<ServerError> {
        val station = localStorage.stationCredentials
            ?: return Result.Error(ServerError(NetworkError.FORBIDDEN, "This device isn't a station"))

        return userClient.pinLogin(station, userIdentifier, pin).also { result ->
            if (result is Result.Success) onLoggedIn()
        }
    }

    /** Finishes an invite, which also logs in */
    suspend fun completeInviteSetup(username: String, request: InviteSetupRequest): EmptyResult<ServerError> =
        userClient.completeInviteSetup(username, request).also { result ->
            if (result is Result.Success) onLoggedIn()
        }

    /**
     * Drops the current session. The saved password is cleared too so the refresh fallback
     * doesn't log us straight back in, the username is kept for prefilling the login form
     */
    suspend fun logout() {
        // Tell the server so this device's session ends, other devices stay logged in
        if (serverUrl.isNotBlank() && localStorage.accessToken.isNotBlank()) {
            networkErrorTryCatch(LOGOUT_TAG) {
                httpClient.post { url("$serverUrl/api/auth/logout") }
            }?.let { response -> handlePlainHttpResponse(LOGOUT_TAG, response) }
        }

        localStorage.clearSession()
        localStorage.password = ""
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        AppLogger.m(LOGOUT_TAG, SimpleResult.SUCCESS)
    }

    /**
     * Ends this device's session but keeps the saved login, used when a kiosk locks
     */
    suspend fun endSession() {
        if (serverUrl.isNotBlank() && localStorage.accessToken.isNotBlank()) {
            networkErrorTryCatch(LOGOUT_TAG) {
                httpClient.post { url("$serverUrl/api/auth/logout") }
            }?.let { response -> handlePlainHttpResponse(LOGOUT_TAG, response) }
        }

        localStorage.clearSession()
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }

    /** People who can PIN in at this station, empty if it isn't one */
    suspend fun stationStaff(): Result<List<StaffMember>, ServerError> {
        val station = localStorage.stationCredentials
            ?: return Result.Error(ServerError(NetworkError.FORBIDDEN, "This device isn't a station"))

        return userClient.stationStaff(station)
    }

    /**
     * Drop the tokens the http client cached before login so the next request loads the new ones
     */
    private fun onLoggedIn() {
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
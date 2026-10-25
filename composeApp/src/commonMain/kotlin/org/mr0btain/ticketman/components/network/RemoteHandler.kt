package org.mr0btain.ticketman.components.network

import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.AuthPaths
import org.mr0btain.ticketman.server.UserPaths
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
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.components.network.user.ServerCheck
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult

/**
 * Handles the interaction and states of server side stuff
 */
class RemoteHandler(
    engine: HttpClientEngine,
    private val localStorage: LocalStorage,
    /** Self-made server certificates this device trusts. Has to be the one [engine] was built with */
    val trust: ServerTrust = ServerTrust(localStorage)
) {
    private val TAG = "ServerHandler"
    private val SET_SERVER_URL_TAG = "$TAG:SetServerUrl"
    private val REFRESH_TOKENS_TAG = "$TAG:RefreshTokens"
    private val LOGIN_TAG = "$TAG:Login"
    private val LOGOUT_TAG = "$TAG:Logout"
    private val KIOSK_TAG = "$TAG:Kiosk"
    val userClient = ServerUserClient(createUserHttpClient(engine), localStorage)

    // Shared with the photo loader so photos go out with the same tokens and refresh
    val httpClient = createRemoteHttpClient(
        engine = engine,
        getAccessToken = { getAccessToken() },
        getRefreshToken = { getRefreshToken() },
        refreshTokens = { refreshTokens() }
    )

    // One per area of the API, all on the same logged in client
    val customerRepo = CustomerRemoteRepository(httpClient)
    val ticketRepo = TicketRemoteRepository(httpClient)
    val inventoryRepo = InventoryRemoteRepository(httpClient)
    val userRepo = UserRemoteRepository(httpClient)
    val adminRepo = AdminRemoteRepository(httpClient)
    val counterRepo = CounterRemoteRepository(httpClient)
    val invoiceRepo = InvoiceRemoteRepository(httpClient)
    val imageRepo = ImageRemoteRepository(httpClient)
    val notifyRepo = NotifyRemoteRepository(httpClient)
    val estimateRepo = EstimateRemoteRepository(httpClient)
    val giftCardRepo = GiftCardRemoteRepository(httpClient)

    /** Every repository, so [setUrl] points all of them at the server. A new one goes here too */
    private val repositories: List<ServerRepository> =
        listOf(customerRepo, ticketRepo, inventoryRepo, userRepo, adminRepo, counterRepo, invoiceRepo, imageRepo, notifyRepo, estimateRepo, giftCardRepo)

    /**
     * Whether an expired session can be fixed by logging in again with the saved username and password.
     * Kiosks turn this off so a locked counter can't quietly sign the device's owner back in
     */
    var credentialFallback: Boolean = true

    // Base url once a server has been reached, used for routes outside the repositories
    private var serverUrl = ""

    /**
     * Why this app and the server the last [setUrl] reached may not work together, null when they match. Only means
     * something after a [setUrl] that connected. Older servers don't say their version, which counts as a mismatch
     */
    val apiMismatch: String?
        get() = userClient.serverApiVersion.let { version ->
            when {
                version == API_VERSION -> null
                version == null || version < API_VERSION -> "The server is older than this app, so some things may not work. Update the server."
                else -> "The server is newer than this app, so some things may not work. Update the app."
            }
        }

    /**
     * The address the last [setUrl] went to. Not always the one it was given: a plain HTTP address the server
     * redirects becomes its `https://` one
     */
    var address = ""
        private set

    // Emits when the session can't be refreshed and the user needs to log in again
    private val _authExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authExpired = _authExpired.asSharedFlow()

    /**
     * Sets url to the server in all clients and tries to log in with the stored credentials
     */
    suspend fun setUrl(requested: String): ConnectionResult {
        // A server that only takes HTTPS redirects plain HTTP, so go straight to where it points
        val url = userClient.httpsRedirectOf(requested) ?: requested
        address = url

        if (url.isNotBlank()) {
            trust.clearRefused()

            // Check if we can establish a connection with this address, if success then set it.
            val check = userClient.checkServer(url)
            if (check == ServerCheck.UP) {
                userClient.changeServerUrl(url)
                serverUrl = url.trimEnd('/')
                repositories.forEach { it.serverUrl = url }

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
            if (check == ServerCheck.PROXY_ONLY) {
                AppLogger.i(SET_SERVER_URL_TAG, "A proxy answered at $url, but not the server behind it")
                return ConnectionResult.SERVER_NOT_ANSWERING
            }

            // Reached, but its certificate needs someone to check it first
            val refused = trust.refused.value
            if (refused != null && refused.address == trustAddressOf(url)) {
                AppLogger.i(SET_SERVER_URL_TAG, "Certificate for ${refused.address} isn't trusted yet")
                return ConnectionResult.UNTRUSTED
            }

            // Reached by a name its certificate doesn't cover
            val wrongName = trust.wrongName.value
            if (wrongName != null && wrongName.address == trustAddressOf(url)) {
                AppLogger.i(SET_SERVER_URL_TAG, "Certificate for ${wrongName.address} is for ${wrongName.names}")
                return ConnectionResult.WRONG_NAME
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
            if (userClient.refreshTokens(localStorage.refreshToken) is TmResult.Success) {
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
            ?: return TmResult.Error(ServerError(NetworkError.FORBIDDEN, "This device isn't a station"))

        return userClient.pinLogin(station, userIdentifier, pin).also { result ->
            if (result is TmResult.Success) onLoggedIn()
        }
    }

    /** Finishes an invite, which also logs in */
    suspend fun completeInviteSetup(username: String, request: InviteSetupRequest): EmptyResult<ServerError> =
        userClient.completeInviteSetup(username, request).also { result ->
            if (result is TmResult.Success) onLoggedIn()
        }

    /**
     * Drops the current session. The saved password is cleared too so the refresh fallback
     * doesn't log us straight back in, the username is kept for prefilling the login form
     */
    suspend fun logout() {
        // Tell the server so this device's session ends, other devices stay logged in
        if (serverUrl.isNotBlank() && localStorage.accessToken.isNotBlank()) {
            networkErrorTryCatch(LOGOUT_TAG) {
                httpClient.post { url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.LOGOUT}") }
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
                httpClient.post { url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.LOGOUT}") }
            }?.let { response -> handlePlainHttpResponse(LOGOUT_TAG, response) }
        }

        localStorage.clearSession()
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }

    /**
     * Lets the server know someone is still using this kiosk, so their PIN session doesn't run out while they
     * work without loading anything. Any request does it, this one is small
     */
    suspend fun keepKioskAlive() {
        if (serverUrl.isBlank() || localStorage.accessToken.isBlank()) return

        networkErrorTryCatch(KIOSK_TAG) {
            httpClient.get { url("$serverUrl${ApiRoutes.USER}${UserPaths.ME}") }
        }
    }

    /** Tells the server an admin left kiosk mode here, so their PIN session stops ending with the idle lock */
    suspend fun leaveKiosk() {
        if (serverUrl.isBlank()) return

        networkErrorTryCatch(KIOSK_TAG) {
            httpClient.post { url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.KIOSK_LEAVE}") }
        }?.let { response -> handlePlainHttpResponse(KIOSK_TAG, response) }
    }

    /** People who can PIN in at this station, empty if it isn't one */
    suspend fun stationStaff(): TmResult<List<StaffMember>, ServerError> {
        val station = localStorage.stationCredentials
            ?: return TmResult.Error(ServerError(NetworkError.FORBIDDEN, "This device isn't a station"))

        return userClient.stationStaff(station)
    }

    /**
     * Drop the tokens the http client cached before login so the next request loads the new ones
     */
    private fun onLoggedIn() {
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
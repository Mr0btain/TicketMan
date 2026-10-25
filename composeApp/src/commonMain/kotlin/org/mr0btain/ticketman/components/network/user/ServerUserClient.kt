package org.mr0btain.ticketman.components.network.user

import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.API_VERSION_HEADER
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.TimeClockPaths
import org.mr0btain.ticketman.data.timeclock.PunchResult
import org.mr0btain.ticketman.server.AuthPaths
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorFor
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.InviteSetupRequest
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationCredentials
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PinLoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginStatus
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Main Client for user services with the server
 */
@OptIn(ExperimentalUuidApi::class)
class ServerUserClient(
    private val userHttpClient: HttpClient,
    private val localStorage: LocalStorage
) {
    companion object {
        private const val TAG = "ServerUserClient"
        private const val LOGIN_TAG = "$TAG:Login"
        private const val CHANGE_SERVER_TAG = "$TAG:ChangeServer"
        private const val CHECK_CREDENTIALS_TAG = "$TAG:CheckCredentials"
        private const val REFRESH_TOKEN_TAG = "$TAG:RefreshTokens"
        private const val CHECK_SERVER_CONNECTION_URL_TAG = "$TAG:CheckServerConnectionURL"
        private const val REQUEST_ACCOUNT_TAG = "$TAG:RequestAccount"
        private const val INVITE_SETUP_TAG = "$TAG:InviteSetup"
        private const val PIN_LOGIN_TAG = "$TAG:PinLogin"
        private const val STATION_TAG = "$TAG:Station"
    }

    private var serverUrl = ""

    /**
     * Changes the url stored in this class
     */
    fun changeServerUrl(url: String) {
        serverUrl = url
        AppLogger.m(CHANGE_SERVER_TAG, SimpleResult.SUCCESS)
    }

    /**
     * Log in to server using supplied credentials. An invite's setup code can go in place of the password
     */
    suspend fun login(username: String = localStorage.username, password: String = localStorage.password): LoginOutcome {
        // Nothing to log in with, this happens when credentials aren't remembered
        if (username.isBlank() || password.isBlank()) {
            AppLogger.i(LOGIN_TAG, "FAIL: Missing credentials")
            return LoginOutcome.Failed(NetworkError.UNAUTHORIZED)
        }

        if (serverUrl.isBlank()) return LoginOutcome.Failed(NetworkError.NO_INTERNET)

        // The login call itself says whether the server is there, so a timeout isn't taken for a wrong password
        val response = networkRequest(LOGIN_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.AUTH}")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password, localStorage.deviceId, localStorage.deviceName))
            }
        }

        val result = when (response) {
            is TmResult.Error -> response
            is TmResult.Success -> handleHttpResponse<AuthResponse>(LOGIN_TAG, response.data)
        }

        result.onSuccess { authResponse ->
            when (authResponse.status) {
                LoginStatus.PENDING_APPROVAL -> return LoginOutcome.PendingApproval

                LoginStatus.INVITE_SETUP -> authResponse.setupToken?.let { token ->
                    return LoginOutcome.InviteSetup(username, token)
                }

                LoginStatus.OK, LoginStatus.NEEDS_SETUP -> if (storeSession(authResponse, username, password)) {
                    return LoginOutcome.LoggedIn(needsSetup = authResponse.status == LoginStatus.NEEDS_SETUP)
                }
            }
        }

        AppLogger.i(LOGIN_TAG, "FAIL")
        // Accepted but missing its tokens is a reply the app can't use
        return LoginOutcome.Failed((result as? TmResult.Error)?.error ?: NetworkError.SERIALIZATION)
    }

    // Keeps the tokens, and the credentials if they should be remembered
    private fun storeSession(authResponse: AuthResponse, username: String, password: String): Boolean {
        val access = authResponse.accessToken ?: return false
        val refresh = authResponse.refreshToken ?: return false

        localStorage.accessToken = access
        localStorage.refreshToken = refresh

        // Only keep credentials around if the user wants them remembered
        if (localStorage.rememberLogin) {
            localStorage.username = username
            localStorage.password = password
        }

        return true
    }

    /**
     * Switches to a user at this station with their PIN. Stores their tokens but not their credentials,
     * a PIN isn't something to log back in with
     */
    suspend fun pinLogin(station: StationCredentials, userIdentifier: Uuid, pin: String): EmptyResult<ServerError> {
        val response = networkRequest(PIN_LOGIN_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.PIN}")
                contentType(ContentType.Application.Json)
                setBody(PinLoginRequest(station, userIdentifier, pin))
            }
        }

        return when (val result = handleDataHttpResponse<AuthResponse>(PIN_LOGIN_TAG, response)) {
            is TmResult.Success -> {
                val access = result.data.accessToken
                val refresh = result.data.refreshToken

                if (access != null && refresh != null) {
                    localStorage.accessToken = access
                    localStorage.refreshToken = refresh
                    TmResult.Success(Unit)
                } else TmResult.Error(ServerError(NetworkError.UNKNOWN))
            }

            is TmResult.Error -> result
        }
    }

    /** This station's record, null if it isn't registered or the server doesn't know it */
    suspend fun stationStatus(station: StationCredentials): TmResult<Station, ServerError> {
        val response = networkRequest(STATION_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.STATION_STATUS}")
                contentType(ContentType.Application.Json)
                setBody(station)
            }
        }

        // NOT_FOUND means the server doesn't know this station (removed, or a restore from before it was made)
        return handleDataHttpResponse(STATION_TAG, response)
    }

    /** People who can PIN in here */
    /** Clocks someone in or out at this station by their PIN. Sent without a login, so a locked kiosk can do it */
    suspend fun stationPunch(station: StationCredentials, userIdentifier: Uuid, pin: String): TmResult<PunchResult, ServerError> {
        val response = networkRequest(STATION_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.TIME_CLOCK}${TimeClockPaths.PUNCH}")
                contentType(ContentType.Application.Json)
                setBody(PinLoginRequest(station, userIdentifier, pin))
            }
        }

        return handleDataHttpResponse(STATION_TAG, response)
    }

    suspend fun stationStaff(station: StationCredentials): TmResult<List<StaffMember>, ServerError> {
        val response = networkRequest(STATION_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.STATION_STAFF}")
                contentType(ContentType.Application.Json)
                setBody(station)
            }
        }

        return handleDataHttpResponse(STATION_TAG, response)
    }

    /** Asks for an account, an admin has to approve it before it can be used */
    suspend fun requestAccount(request: AccountRequest): EmptyResult<ServerError> {
        val response = networkRequest(REQUEST_ACCOUNT_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.REQUEST_ACCOUNT}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        return handleActionHttpResponse(REQUEST_ACCOUNT_TAG, response)
    }

    /**
     * Finishes an invite and logs in with the new password
     */
    suspend fun completeInviteSetup(username: String, request: InviteSetupRequest): EmptyResult<ServerError> {
        val response = networkRequest(INVITE_SETUP_TAG) {
            userHttpClient.post {
                url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.SETUP_INVITE}")
                contentType(ContentType.Application.Json)
                setBody(request.copy(deviceId = localStorage.deviceId, deviceName = localStorage.deviceName))
            }
        }

        return when (val result = handleDataHttpResponse<AuthResponse>(INVITE_SETUP_TAG, response)) {
            is TmResult.Success -> if (storeSession(result.data, username, request.password)) TmResult.Success(Unit)
                else TmResult.Error(ServerError(NetworkError.UNKNOWN))
            is TmResult.Error -> result
        }
    }

    /**
     * Refresh tokens for authentication with server
     */
    suspend fun refreshTokens(token: String): TmResult<AuthResponse, NetworkError> {
        if (checkServerConnection()) {
            networkErrorTryCatch(REFRESH_TOKEN_TAG) {
                userHttpClient.post {
                    url("$serverUrl${ApiRoutes.AUTH}${AuthPaths.REFRESH}")
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(token))
                }
            }?.let { response -> handleHttpResponse<RefreshTokenResponse>(REFRESH_TOKEN_TAG, response) }
                ?.onSuccess { tokens ->
                    localStorage.accessToken = tokens.token
                    // The server swaps the refresh token each time, the one just sent won't work again
                    tokens.refreshToken?.let { localStorage.refreshToken = it }

                    // We don't use UUID here so just slap whatever. Maybe make another data class for this??
                    return TmResult.Success(AuthResponse(Uuid.generateV7(), tokens.token, tokens.refreshToken ?: token))
                }
        }

        return TmResult.Error(NetworkError.UNKNOWN)
    }

    /**
     * Checks the connection with the stored server url
     */
    suspend fun checkServerConnection(): Boolean = checkServerConnection(serverUrl)

    /**
     * The `https://` address a plain `http://` [url] redirects to, when the server has `HTTP_MODE=redirect`.
     * Null for an `https://` address, or one that answers without redirecting
     */
    suspend fun httpsRedirectOf(url: String): String? {
        if (!url.startsWith("http://", ignoreCase = true)) return null
        val base = url.trimEnd('/')

        val ping = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) { userHttpClient.get("$base$SERVER_PING_PATH") }
            ?: return null
        if (ping.status.value !in 300..399) return null

        // Only taken when it's the same server's ping on HTTPS, not somewhere else entirely
        val target = ping.headers[HttpHeaders.Location]?.takeIf { it.endsWith(SERVER_PING_PATH) } ?: return null
        val moved = target.removeSuffix(SERVER_PING_PATH)
        val sameHost = try {
            Url(moved).let { it.protocol == URLProtocol.HTTPS && it.host == Url(base).host }
        } catch (ex: Exception) {
            false
        }

        return moved.takeIf { sameHost }?.also { AppLogger.i(CHECK_SERVER_CONNECTION_URL_TAG, "$base redirects to $it") }
    }

    /**
     * The [API_VERSION] the server reported the last time [checkServerConnection] reached it, null for a server from
     * before it reported one
     */
    var serverApiVersion: Int? = null
        private set

    /**
     * Whether a TicketMan server answers at [url]. Asks `/api/ping`, then `/` for servers older than that. Only a
     * reply that isn't a web page counts from `/`, so the web client's dev server isn't mistaken for one. Also notes
     * the server's [serverApiVersion]
     */
    suspend fun checkServerConnection(url: String): Boolean = checkServer(url) == ServerCheck.UP

    /** Like [checkServerConnection], also saying when a reverse proxy answered for a server that didn't */
    suspend fun checkServer(url: String): ServerCheck {
        serverApiVersion = null
        if (url.isBlank()) return ServerCheck.DOWN
        val base = url.trimEnd('/')

        val ping = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) { userHttpClient.get("$base$SERVER_PING_PATH") }
            ?: return ServerCheck.DOWN
        if (ping.status == HttpStatusCode.OK) {
            serverApiVersion = ping.headers[API_VERSION_HEADER]?.trim()?.toIntOrNull()
            return if (ping.bodyAsText().trim() == SERVER_PING_BODY) ServerCheck.UP else ServerCheck.DOWN
        }
        if (networkErrorFor(ping.status.value) == NetworkError.PROXY_SERVER_DOWN) return ServerCheck.PROXY_ONLY
        if (ping.status != HttpStatusCode.NotFound) return ServerCheck.DOWN

        val root = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) { userHttpClient.get("$base/") } ?: return ServerCheck.DOWN
        val up = root.status == HttpStatusCode.OK && root.contentType()?.match(ContentType.Text.Html) != true
        return if (up) ServerCheck.UP else ServerCheck.DOWN
    }
}

/** What answered at an address, from [ServerUserClient.checkServer] */
enum class ServerCheck {
    /** A TicketMan server */
    UP,

    /** A reverse proxy, saying the server behind it isn't answering (502, 503, 504) */
    PROXY_ONLY,

    /** Nothing, or something that isn't TicketMan */
    DOWN
}
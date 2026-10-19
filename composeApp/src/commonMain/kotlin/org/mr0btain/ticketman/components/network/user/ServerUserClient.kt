package org.mr0btain.ticketman.components.network.user

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
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.Result
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
// TODO: Setup getting user info from server and setting current user after we get data
// TODO: Find new way of using identifier, maybe generate a random uuid each time and use that for the duration of the program?
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
                url("$serverUrl/api/auth")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password, localStorage.deviceId, localStorage.deviceName))
            }
        }

        val result = when (response) {
            is Result.Error -> response
            is Result.Success -> handleHttpResponse<AuthResponse>(LOGIN_TAG, response.data)
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
        return LoginOutcome.Failed((result as? Result.Error)?.error ?: NetworkError.SERIALIZATION)
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
                url("$serverUrl/api/auth/pin")
                contentType(ContentType.Application.Json)
                setBody(PinLoginRequest(station, userIdentifier, pin))
            }
        }

        return when (val result = handleDataHttpResponse<AuthResponse>(PIN_LOGIN_TAG, response)) {
            is Result.Success -> {
                val access = result.data.accessToken
                val refresh = result.data.refreshToken

                if (access != null && refresh != null) {
                    localStorage.accessToken = access
                    localStorage.refreshToken = refresh
                    Result.Success(Unit)
                } else Result.Error(ServerError(NetworkError.UNKNOWN))
            }

            is Result.Error -> result
        }
    }

    /** This station's record, null if it isn't registered or the server doesn't know it */
    suspend fun stationStatus(station: StationCredentials): Station? {
        val response = networkErrorTryCatch(STATION_TAG) {
            userHttpClient.post {
                url("$serverUrl/api/auth/station/status")
                contentType(ContentType.Application.Json)
                setBody(station)
            }
        } ?: return null

        handleHttpResponse<Station>(STATION_TAG, response).onSuccess { return it }
        return null
    }

    /** People who can PIN in here */
    suspend fun stationStaff(station: StationCredentials): Result<List<StaffMember>, ServerError> {
        val response = networkRequest(STATION_TAG) {
            userHttpClient.post {
                url("$serverUrl/api/auth/station/staff")
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
                url("$serverUrl/api/auth/request-account")
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
                url("$serverUrl/api/auth/setup/invite")
                contentType(ContentType.Application.Json)
                setBody(request.copy(deviceId = localStorage.deviceId, deviceName = localStorage.deviceName))
            }
        }

        return when (val result = handleDataHttpResponse<AuthResponse>(INVITE_SETUP_TAG, response)) {
            is Result.Success -> if (storeSession(result.data, username, request.password)) Result.Success(Unit)
                else Result.Error(ServerError(NetworkError.UNKNOWN))
            is Result.Error -> result
        }
    }

    /**
     * Refresh tokens for authentication with server
     */
    suspend fun refreshTokens(token: String): Result<AuthResponse, NetworkError> {
        if (checkServerConnection()) {
            networkErrorTryCatch(REFRESH_TOKEN_TAG) {
                userHttpClient.post {
                    url("$serverUrl/api/auth/refresh")
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(token))
                }
            }?.let { response -> handleHttpResponse<RefreshTokenResponse>(REFRESH_TOKEN_TAG, response) }
                ?.onSuccess { tokens ->
                    localStorage.accessToken = tokens.token
                    // The server swaps the refresh token each time, the one just sent won't work again
                    tokens.refreshToken?.let { localStorage.refreshToken = it }

                    // We don't use UUID here so just slap whatever. Maybe make another data class for this??
                    return Result.Success(AuthResponse(Uuid.generateV7(), tokens.token, tokens.refreshToken ?: token))
                }
        }

        return Result.Error(NetworkError.UNKNOWN)
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
     * Whether a TicketMan server answers at [url]. Asks `/api/ping`, then `/` for servers older than that. Only a
     * reply that isn't a web page counts from `/`, so the web client's dev server isn't mistaken for one
     */
    suspend fun checkServerConnection(url: String): Boolean {
        if (url.isBlank()) return false
        val base = url.trimEnd('/')

        val ping = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) { userHttpClient.get("$base$SERVER_PING_PATH") }
            ?: return false
        if (ping.status == HttpStatusCode.OK) return ping.bodyAsText().trim() == SERVER_PING_BODY
        if (ping.status != HttpStatusCode.NotFound) return false

        val root = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) { userHttpClient.get("$base/") } ?: return false
        return root.status == HttpStatusCode.OK && root.contentType()?.match(ContentType.Text.Html) != true
    }
}
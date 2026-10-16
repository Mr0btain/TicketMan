package org.mr0btain.ticketman.components.network.user

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
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
    private val remoteHandler: RemoteHandler
) {
    companion object {
        private const val TAG = "ServerUserClient"
        private const val LOGIN_TAG = "$TAG:Login"
        private const val CHANGE_SERVER_TAG = "$TAG:ChangeServer"
        private const val CHECK_CREDENTIALS_TAG = "$TAG:CheckCredentials"
        private const val REFRESH_TOKEN_TAG = "$TAG:RefreshTokens"
        private const val CHECK_SERVER_CONNECTION_TAG = "$TAG:CheckServerConnection"
        private const val CHECK_SERVER_CONNECTION_URL_TAG = "$TAG:CheckServerConnectionURL"
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
     * Log in to server using stored credentials
     */
    suspend fun login(): Boolean {
        // Get username and password
        val username = remoteHandler.getUsername()
        val password = remoteHandler.getPassword()

        return login(username, password)
    }

    /**
     * Log in to server using supplied credentials
     */
    suspend fun login(username: String, password: String): Boolean {
        if (checkServerConnection()) {
            networkErrorTryCatch(LOGIN_TAG) {
                userHttpClient.post {
                    url("$serverUrl/api/auth")
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username, password))
                }
            }?.let { response -> handleHttpResponse<AuthResponse>(LOGIN_TAG, response) }
                ?.onSuccess { authResponse ->
                    println(authResponse)
                    if (authResponse.refreshToken != null && authResponse.accessToken != null) {
                        remoteHandler.updateAuthTokens(authResponse.accessToken!!, authResponse.refreshToken!!)
                        return true
                    }
                }

            AppLogger.i(LOGIN_TAG, "FAIL")
        }

        return false
    }

    /**
     * Check if credentials are still good in database
     */
    suspend fun checkLoginCredentials(username: String, password: String): Boolean {
        val response = networkErrorTryCatch(CHECK_CREDENTIALS_TAG) {
            userHttpClient.post {
                url("$serverUrl/api/auth/user")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
        }

        return if (response != null && response.status == HttpStatusCode.Companion.Accepted) {
            AppLogger.m(CHECK_CREDENTIALS_TAG, "SUCCESS")
            true
        } else {
            AppLogger.i(CHECK_CREDENTIALS_TAG, "FAIL")
            false
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
                ?.onSuccess { accessToken ->
                    remoteHandler.updateAuthTokens(accessToken.token, token)

                    // We don't use UUID here so just slap whatever. Maybe make another data class for this??
                    return Result.Success(AuthResponse(Uuid.generateV7(), accessToken.token, token))
                }
        }

        return Result.Error(NetworkError.UNKNOWN)
    }

    /**
     * Checks the connection with the stored server url
     */
    suspend fun checkServerConnection(): Boolean {
        return if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(CHECK_SERVER_CONNECTION_TAG) {
                userHttpClient.get("${serverUrl}/")
            }

            if (response != null && response.status == HttpStatusCode.OK) {
                true
            } else false
        } else false
    }

    /**
     * Checks the connection with a specific server url
     */
    suspend fun checkServerConnection(url: String): Boolean {
        return if (url.isNotBlank()) {
            val response = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) {
                userHttpClient.get("$url/")
            }

            response != null && response.status == HttpStatusCode.OK
        } else false
    }
}
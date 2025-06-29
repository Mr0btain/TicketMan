package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerializationException
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import org.mr0btain.ticketman.server.response.TokenResponse
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.ServerHandler
import java.util.*

/**
 * Main Client for user services with the server
 */
// TODO: Figure out why we keep getting messed up urls when we set this from outside??
class ServerUserClient(
    private val userHttpClient: HttpClient,
    private val serverHandler: ServerHandler
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


    private var serverUrl = "http://localhost:8080"

    /**
     * Changes the url stored in this class
     */
    fun changeServerUrl(url: String) {
        println("URL saved: $url")
        //serverUrl = url
        AppLogger.i(CHANGE_SERVER_TAG, SimpleError.SUCCESS)
    }

    /**
     * Log in to server using stored credentials
     */
    @OptIn(InternalAPI::class)
    suspend fun login(): Boolean {
        // Get username and password
        val username = serverHandler.getUsername()
        val password = serverHandler.getPassword()

        return login(username, password)
    }

    /**
     * Log in to server using supplied credentials
     */
    suspend fun login(username: String, password: String): Boolean {
        networkErrorTryCatch(LOGIN_TAG) {
            userHttpClient.post {
                url("$serverUrl/api/auth")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
        }?.let { response -> handleHttpResponse<AuthResponse>(LOGIN_TAG, response) }
            ?.onSuccess { authResponse ->
                serverHandler.updateAuthTokens(authResponse.accessToken, authResponse.refreshToken)
                return true
            }

        AppLogger.i(LOGIN_TAG, "FAIL")

        return false
    }

    /**
     * Check if credentials are still good in database
     */
    suspend fun checkCredentials(username: String, password: String): Boolean {
        val response = networkErrorTryCatch(CHECK_CREDENTIALS_TAG) {
            userHttpClient.post {
                url("$serverUrl/api/auth/user")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
        }

        return if (response != null && response.status == HttpStatusCode.Accepted) {
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
        networkErrorTryCatch(REFRESH_TOKEN_TAG) {
            userHttpClient.post {
                url("$serverUrl/api/auth/refresh")
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(token))
            }
        }?.let { response -> handleHttpResponse<RefreshTokenResponse>(REFRESH_TOKEN_TAG, response) }
            ?.onSuccess { accessToken ->
                serverHandler.updateAuthTokens(accessToken.token, token)
                // We don't use UUID here so just slap whatever. Maybe make another data class for this??
                return Result.Success(AuthResponse("", accessToken.token, token))
            }

        return Result.Error(NetworkError.UNKNOWN)
    }

    /**
     * Checks the connection with the stored server url
     */
    suspend fun checkServerConnection(): Boolean {
        val response = networkErrorTryCatch(CHECK_SERVER_CONNECTION_TAG) {
            userHttpClient.get("${serverUrl}/")
        }

        return response != null && response.status == HttpStatusCode.OK
    }

    /**
     * Checks the connection with a specific server url
     */
    suspend fun checkServerConnection(url: String): Boolean {
        val response = networkErrorTryCatch(CHECK_SERVER_CONNECTION_URL_TAG) {
            userHttpClient.get("$url/")
        }

        return response != null && response.status == HttpStatusCode.OK
    }
}
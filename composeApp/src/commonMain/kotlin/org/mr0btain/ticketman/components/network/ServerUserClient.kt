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
import org.mr0btain.ticketman.components.network.ServerDatabaseClient.Companion.TAG
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.ServerHandler
import java.util.*

/**
 * Main Client for user services with the server
 */
class ServerUserClient(
    private val userHttpClient: HttpClient,
    private val serverHandler: ServerHandler
) {
    companion object {
        const val TAG = "ServerUserClient"
    }

    private var serverUrl = ""

    /**
     * Changes the url stored in this class
     */
    fun changeServerUrl(url: String) {
        serverUrl = url
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
        val response = try {
            // Send request and receive response
            userHttpClient.post {
                url("$serverUrl/api/auth")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
        } catch (ex: UnresolvedAddressException) {
            AppLogger.m("$TAG:login", ex.toString())
            return false
        } catch (ex: SerializationException) {
            AppLogger.m("$TAG:login", ex.toString())
            return false
        } catch (ex: Exception) {
            AppLogger.m("$TAG:login", ex.toString())
            return false
        }

        handleHttpResponse<AuthResponse>(response)
            .onSuccess { authResponse ->
                serverHandler.updateAuthTokens(authResponse.accessToken, authResponse.refreshToken)
                AppLogger.m("$TAG:login", "SUCCESS")
                return true
            }

        AppLogger.i("$TAG:login", "FAIL")

        return false
    }

    /**
     * Refresh tokens for authentication with server
     */
    suspend fun refreshTokens(token: String): Result<AuthResponse, NetworkError> {
        val response = try {
            // Send request and receive response
            userHttpClient.post {
                url("$serverUrl/api/auth/refresh")
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(token))
            }
        } catch (ex: UnresolvedAddressException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.NO_INTERNET)
        } catch (ex: SerializationException) {
            ex.printStackTrace()
            return Result.Error(NetworkError.SERIALIZATION)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Result.Error(NetworkError.UNKNOWN)
        }

        // Check if we get a token back or not, else we need to re login
        handleHttpResponse<RefreshTokenResponse>(response)
            .onSuccess { return Result.Success(AuthResponse(UUID.randomUUID(), it.token, token)) }

        return Result.Error(NetworkError.UNKNOWN)
    }

    /**
     * Checks the connection with the stored server url
     */
    suspend fun checkServerConnection(): Boolean {
        val response = try {
            userHttpClient.get("${serverUrl}/")
        } catch (ex: UnresolvedAddressException) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        } catch (ex: SerializationException) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        } catch (ex: Exception) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        }

        return response.status == HttpStatusCode.OK
    }

    /**
     * Checks the connection with a specific server url
     */
    suspend fun checkServerConnection(url: String): Boolean {
        val response = try {
            userHttpClient.get("$url/")
        } catch (ex: UnresolvedAddressException) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        } catch (ex: SerializationException) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        } catch (ex: Exception) {
            AppLogger.m("$TAG:CheckServerConnection", ex.toString())
            return false
        }

        return response.status == HttpStatusCode.OK
    }
}

private suspend inline fun <reified T> handleHttpResponse(response: HttpResponse): Result<T, NetworkError> {
    return when (response.status.value) {
        in 200..299 -> {
            val response = response.body<T>()
            AppLogger.m("$TAG:CheckServerConnection", "SUCCESS")
            Result.Success(response)
        }

        401 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.UNAUTHORIZED.name)
            Result.Error(NetworkError.UNAUTHORIZED)
        }

        409 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.CONFLICT.name)
            Result.Error(NetworkError.CONFLICT)
        }

        408 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.REQUEST_TIMEOUT.name)
            Result.Error(NetworkError.REQUEST_TIMEOUT)
        }

        413 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.PAYLOAD_TOO_LARGE.name)
            Result.Error(NetworkError.PAYLOAD_TOO_LARGE)
        }

        in 500..599 -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.SERVER_ERROR.name)
            Result.Error(NetworkError.SERVER_ERROR)
        }

        else -> {
            AppLogger.m("$TAG:CheckServerConnection", NetworkError.UNKNOWN.name)
            Result.Error(NetworkError.UNKNOWN)
        }
    }
}
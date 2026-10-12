package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.*
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.database.*
import org.mr0btain.ticketman.components.network.user.ServerUserClient
import org.mr0btain.ticketman.components.network.user.createUserHttpClient
import org.mr0btain.ticketman.data.token.Token
import org.mr0btain.ticketman.data.user.User
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
    val userClient = ServerUserClient(createUserHttpClient(engine), this)

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
    
    /**
     * Sets url to the server in all clients
     */
    suspend fun setUrl(url: String) {
        if (url.isNotBlank()) {
            // Check if we can establish a connection with this address, if success then set it.
            if (userClient.checkServerConnection(url)) {
                userClient.changeServerUrl(url)
                customerRepo.serverUrl = url
                ticketRepo.serverUrl = url
                inventoryRepo.serverUrl = url

                AppLogger.m(SET_SERVER_URL_TAG, SimpleResult.SUCCESS)
            }
        } else {
            AppLogger.m(SET_SERVER_URL_TAG, "FAIL: Url is empty")
        }
    }

    /**
     * Gets stored username from database
     */
    suspend fun getUsername(): String {
        // dataHandler.getStoredUsername()
        return "admin"
    }

    /**
     * Gets stored password from database
     */
    suspend fun getPassword(): String {
        // dataHandler.getStoredPassword()
        return "password"
    }

    /**
     * Gets stored access token from database
     */
    fun getAccessToken() = localStorage.getAccessToken()

    /**
     * Gets both stored tokens from database
     */
    fun getRefreshToken() = localStorage.getRefreshToken()

    /**
     * Gets both stored tokens from database
     */
    suspend fun setUser(user: User) =  "" //dataHandler.userDataHandler.setCurrent(user)

    /**
     * Refreshes tokens for authentication
     */
    fun refreshTokens(): Token {
        // TODO
        return Token("", "", "")
    }

    /**
     * Updates stored tokens in database
     */
    suspend fun updateAuthTokens(access: String, refresh: String) = "" //dataHandler.updateStoredAuthTokens(access, refresh)

    /**
     * Try to log in to server with username and password
     */
    suspend fun login(user: String, pass: String) = userClient.login(user, pass)
}
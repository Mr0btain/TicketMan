package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.*
import org.mr0btain.ticketman.components.network.database.ServerDatabaseClientHandler
import org.mr0btain.ticketman.components.network.database.createDatabaseHttpClient
import org.mr0btain.ticketman.components.network.encryption.ClientEncryptionHandler
import org.mr0btain.ticketman.components.network.user.ServerUserClient
import org.mr0btain.ticketman.components.network.user.createUserHttpClient
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.DataHandler

/**
 * Handles the interaction and states of server clients
 */
class ServerHandler(
    engine: HttpClientEngine,
    private val dataHandler: DataHandler
) {
    private val TAG = "ServerHandler"

    val encryptionHandler = ClientEncryptionHandler()
    val userClient = ServerUserClient(createUserHttpClient(engine), this, encryptionHandler)
    val databaseClient = ServerDatabaseClientHandler(createDatabaseHttpClient(engine, this), encryptionHandler)
    
    /**
     * Sets url to the server in all clients
     */
    suspend fun setUrl(url: String) {
        if (url.isNotBlank()) {
            userClient.changeServerUrl(url)
            databaseClient.changeServerUrl(url)

            // Run a server check so we can update keys and such
            checkServerConnection()
        } else {
            AppLogger.m("$TAG:SetUrl", "FAIL: Url is empty")
        }
    }

    /**
     * Gets stored username from database
     */
    suspend fun getUsername() = dataHandler.getStoredUsername()

    /**
     * Gets stored password from database
     */
    suspend fun getPassword() = dataHandler.getStoredPassword()

    /**
     * Gets stored access token from database
     */
    suspend fun getAccessToken() = dataHandler.getStoredAccessToken()

    /**
     * Gets both stored tokens from database
     */
    suspend fun getRefreshToken() = dataHandler.getStoredRefreshToken()

    /**
     * Gets both stored tokens from database
     */
    suspend fun setUser(user: User) = dataHandler.userDataHandler.setCurrent(user)

    /**
     * Refreshes tokens for authentication
     */
    suspend fun refreshTokens() = dataHandler.refreshTokens()

    /**
     * Updates stored tokens in database
     */
    suspend fun updateAuthTokens(access: String, refresh: String) = dataHandler.updateStoredAuthTokens(access, refresh)

    /**
     * Checks connection with server using stored url
     */
    suspend fun checkServerConnection(): Boolean = userClient.checkServerConnection()

    /**
     * Hits a new server at the specific address and see if we get a response and update clients with url
     */
    suspend fun checkNewServerConnection(url: String): Boolean {
        // Test connection
        val result = userClient.checkServerConnection(url)

        return if (result) {
            // Log success
            AppLogger.m("$TAG:CheckNewServerConnection", SimpleError.SUCCESS)

            // Return success
            true
        } else {
            AppLogger.m("$TAG:CheckNewServerConnection", SimpleError.FAIL)
            false
        }
    }

    /**
     * Try to log in to server with username and password
     */
    suspend fun login(user: String, pass: String) = userClient.login(user, pass)

    /**
     * Sync customers with server database and returns the values local database doesn't have
     */
    suspend fun syncCustomers(currentCustomerList: List<Customer>): List<Customer> = databaseClient.customerClient.sync(currentCustomerList)

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncTickets(currentTickets: List<Ticket>): List<Ticket> = databaseClient.ticketClient.sync(currentTickets)

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncInventory(currentInventory: List<InventoryItem>): List<InventoryItem> = databaseClient.inventoryClient.sync(currentInventory)

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncUsers(currentUsers: List<User>): List<User> = databaseClient.userClient.sync(currentUsers)
}
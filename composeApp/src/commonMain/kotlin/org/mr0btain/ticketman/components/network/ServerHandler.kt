package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine
import org.mr0btain.ticketman.components.network.database.ServerDatabaseClient
import org.mr0btain.ticketman.components.network.database.createDatabaseHttpClient
import org.mr0btain.ticketman.components.network.user.ServerUserClient
import org.mr0btain.ticketman.components.network.user.createUserHttpClient
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

/**
 * Handles the interaction and states of server clients
 */
class ServerHandler(
    engine: HttpClientEngine,
    private val mainViewModel: MainViewModel
) {
    private val TAG = "ServerHandler"

    val userClient = ServerUserClient(createUserHttpClient(engine), this)
    val databaseClient = ServerDatabaseClient(createDatabaseHttpClient(engine, this))

    /**
     * Sets url to the server in all clients
     */
    fun setUrl(url: String) {
        userClient.changeServerUrl(url)
        databaseClient.changeServerUrl(url)
    }

    /**
     * Gets stored username from database
     */
    suspend fun getUsername() = mainViewModel.getStoredUsername()

    /**
     * Gets stored password from database
     */
    suspend fun getPassword() = mainViewModel.getStoredPassword()

    /**
     * Gets stored access token from database
     */
    suspend fun getAccessToken() = mainViewModel.getStoredAccessToken()

    /**
     * Gets both stored tokens from database
     */
    suspend fun getRefreshToken() = mainViewModel.getStoredRefreshToken()

    /**
     * Refreshes tokens for authentication
     */
    suspend fun refreshTokens() = mainViewModel.refreshTokens()

    /**
     * Updates stored tokens in database
     */
    suspend fun updateAuthTokens(access: String, refresh: String) = mainViewModel.updateStoredAuthTokens(access, refresh)

    /**
     * Updates urls clients use
     */
    fun updateServerUrl(url: String) {
        userClient.changeServerUrl(url)
        databaseClient.changeServerUrl(url)
    }

    /**
     * Hits a new server at the specific address and see if we get a response and update clients with url
     */
    suspend fun checkNewServerConnection(url: String): Boolean {
        println("$url")
        return if (url.isNotEmpty()) {
            // Test connection
            val result = userClient.checkServerConnection(url)

            if (result) {
                // Save new address
                mainViewModel.saveServerAddress(url)

                // Update client urls
                updateServerUrl(url)

                // Log success
                AppLogger.m("$TAG:CheckNewServerConnection", "SUCCESS")

                // Return success
                true
            } else {
                // Log fail
                AppLogger.i("$TAG:CheckNewServerConnection", "FAIL")

                // Return result
                false
            }
        } else {
            AppLogger.i("$TAG:CheckNewServerConnection", "FAIL: URL is empty")
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
    suspend fun syncCustomers(currentCustomerList: List<CustomerData>): List<CustomerData> {
        // Handle if our database is empty then no reason to sync just pull
        return if (currentCustomerList.isEmpty()) {
            databaseClient.customerClient.getAll()
        } else {
            // Else go through with sync
            databaseClient.customerClient.sync(currentCustomerList)
        }
    }

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncTickets(currentTickets: List<TicketInfo>): List<TicketInfo> {
        // Handle if our database is empty then no reason to sync just pull
        return if (currentTickets.isEmpty()) {
            databaseClient.ticketClient.getAll()
        } else {
            // Else go through with sync
            databaseClient.ticketClient.sync(currentTickets)
        }
    }

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncInventory(currentInventory: List<InventoryItem>): List<InventoryItem> {
        // Handle if our database is empty then no reason to sync just pull
        return if (currentInventory.isEmpty()) {
            databaseClient.inventoryClient.getAll()
        } else {
            // Else go through with sync
            databaseClient.inventoryClient.sync(currentInventory)
        }
    }

    /**
     * Checks connection with server using stored url
     */
    suspend fun checkServerConnection(): Boolean = userClient.checkServerConnection()
}
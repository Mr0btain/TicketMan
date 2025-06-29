package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import androidx.compose.runtime.mutableStateOf
import io.ktor.client.engine.*
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.components.network.ServerDatabaseClient
import org.mr0btain.ticketman.components.network.ServerUserClient
import org.mr0btain.ticketman.components.network.createDatabaseHttpClient
import org.mr0btain.ticketman.components.network.createUserHttpClient
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

// TODO: Handle user syncing

/**
 * Handles the interaction and states of server clients
 */
class ServerHandler(
    engine: HttpClientEngine,
    private val mainViewModel: MainViewModel
) {
    private val TAG = "ServerHandler"
    private val userHttpClient = mutableStateOf(createUserHttpClient(engine))
    private val databaseHttpClient = mutableStateOf(createDatabaseHttpClient(engine, this))

    val userClient = ServerUserClient(userHttpClient.value, this)
    val databaseClient = ServerDatabaseClient(databaseHttpClient.value)

    suspend fun getUsername() = mainViewModel.getStoredUsername()

    suspend fun getPassword() = mainViewModel.getStoredPassword()

    suspend fun getAccessToken() = mainViewModel.getStoredAccessToken()

    suspend fun getRefreshToken() = mainViewModel.getStoredRefreshToken()

    suspend fun refreshTokens() = mainViewModel.refreshTokens()

    fun updateAuthTokens(access: String, refresh: String) = mainViewModel.updateStoredAuthTokens(access, refresh)

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
            return true
        } else {
            // Log fail
            AppLogger.m("$TAG:CheckNewServerConnection", "FAIL")

            // Return result
            return false
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
            databaseClient.getCustomers()
        } else {
            // Else go through with sync
            databaseClient.syncCustomers(currentCustomerList)
        }
    }

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncTickets(currentTickets: List<TicketInfo>): List<TicketInfo> {
        // Handle if our database is empty then no reason to sync just pull
        return if (currentTickets.isEmpty()) {
            databaseClient.getTickets()
        } else {
            // Else go through with sync
            databaseClient.syncTickets(currentTickets)
        }
    }

    /**
     * Sync inventory with server database and returns the values local database doesn't have
     */
    suspend fun syncInventory(currentInventory: List<InventoryItem>): List<InventoryItem> {
        // Handle if our database is empty then no reason to sync just pull
        return if (currentInventory.isEmpty()) {
            databaseClient.getInventory()
        } else {
            // Else go through with sync
            databaseClient.syncInventory(currentInventory)
        }
    }

    // TODO: It seems okhttp throws stuff into log before we actually reach this and it never makes it to the logs
    private fun handleNetworkError(caller: String, error: NetworkError) {
        when (error) {
            NetworkError.REQUEST_TIMEOUT -> {
                AppLogger.m("$TAG:$caller", error.name)
            }

            NetworkError.UNAUTHORIZED -> {
                AppLogger.i("$TAG:$caller", error.name)
            }

            NetworkError.CONFLICT -> {
                AppLogger.i("$TAG:$caller", error.name)
            }

            NetworkError.TOO_MANY_REQUESTS -> {
                AppLogger.i("$TAG:$caller", error.name)
            }

            NetworkError.NO_INTERNET -> {
                AppLogger.m("$TAG:$caller", error.name)
            }

            NetworkError.PAYLOAD_TOO_LARGE -> {
                AppLogger.i("$TAG:$caller", error.name)
            }

            NetworkError.SERVER_ERROR -> {
                AppLogger.i("$TAG:$caller", error.name)
            }

            NetworkError.SERIALIZATION -> {
                AppLogger.i("$TAG:$caller", error.name)
            }

            NetworkError.UNKNOWN -> {
                AppLogger.i("$TAG:$caller", error.name)
            }
        }
    }

    suspend fun checkServerConnection(): Boolean = userClient.checkServerConnection()
}
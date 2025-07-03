package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.components.invoice.makeTicketInvoicePdf
import org.mr0btain.ticketman.components.data.components.settings.UserSettings
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.network.ServerHandler
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.generateMockData
import org.mr0btain.ticketman.generateRandomInventoryItems
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.util.AppDir

/**
 * Handles all interactions to any data.
 */
class DataHandler(
    appDir: AppDir,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DataHandler"
        private const val SYNC_TAG = "$TAG:Sync"
    }
    private val serverHandler = ServerHandler(getHttpEngine(), this)
    private val clientDatabase = configureDatabase(appDir)
    
    private var autoSync = true

    /** Data Handlers. Mostly just to organise the mess in the view model. Probably not the best way to do it, but it looks much, much better */
    // -----------------------------------------------------------------------------------------------------------------------
    // TODO: Setup access level stuff
    // TODO: Should probably set it up so that we only get regular data for everyone else unless the user is Admin then pull sensitive data.

    val userDataHandler = UserDataHandlerImpl(
        localDatabaseClient = clientDatabase.userDatabase,
        serverDatabaseClient = serverHandler.databaseClient.userClient
    )

    // Inventory handler
    val inventoryDataHandler = InventoryDataHandlerImpl(
        localDatabaseClient = clientDatabase.inventoryDatabase,
        serverDatabaseClient = serverHandler.databaseClient.inventoryClient
    )

    // Ticket handler
    val ticketDataHandler = TicketDataHandlerImpl(
        localDatabaseClient = clientDatabase.ticketDatabase,
        serverDatabaseClient = serverHandler.databaseClient.ticketClient
    )

    // Customer handler
    val customerDataHandler = CustomerDataHandlerImpl(
        localDatabaseClient = clientDatabase.customerDatabase,
        serverDatabaseClient = serverHandler.databaseClient.customerClient
    )
    
    suspend fun syncData(): Boolean {
        return coroutineScope {
            try {
                // Sync customers
                clientDatabase.customerDatabase.getAll()?.let { data ->
                    customerDataHandler.addAll(
                        serverHandler.syncCustomers(data)
                    )
                }
                
                // Sync tickets
                clientDatabase.ticketDatabase.getAll()?.let { data ->
                    ticketDataHandler.addAll(
                        serverHandler.syncTickets(data)
                    )
                }
                
                // Sync inventory
                clientDatabase.inventoryDatabase.getAll()?.let { data ->
                    inventoryDataHandler.addAll(
                        serverHandler.syncInventory(data)
                    )
                }
                
                // Sync users
                clientDatabase.userDatabase.getAll()?.let { data ->
                    userDataHandler.addAll(
                        serverHandler.databaseClient.userClient.sync(data)
                    )
                }

                AppLogger.m(SYNC_TAG, SimpleError.SUCCESS)
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(SYNC_TAG, SimpleError.FAIL)
                false
            }
        }
    }

    /**
     * update stored username
     */
    suspend fun updateStoredUsername(username: String) {
        coroutineScope { clientDatabase.settingsDatabase.setUsername(username) }
    }

    /**
     * Update stored password
     */
    suspend fun updateStoredPassword(password: String) {
        coroutineScope { clientDatabase.settingsDatabase.setPassword(password) }
    }

    /**
     * Update stored access and refresh tokens
     */
    suspend fun updateStoredAuthTokens(access: String, refresh: String) {
        coroutineScope {
            clientDatabase.settingsDatabase.setAccessToken(access)
            clientDatabase.settingsDatabase.setRefreshToken(refresh)
        }
    }

    /**
     * Retrieve stored username
     */
    suspend fun getStoredUsername(): String? =
        coroutineScope { clientDatabase.settingsDatabase.getUsername() }

    /**
     * Retrieve stored access password
     */
    suspend fun getStoredPassword(): String? =
        coroutineScope { clientDatabase.settingsDatabase.getPassword() }

    /**
     * Retrieve stored access token
     */
    suspend fun getStoredAccessToken(): String? =
        coroutineScope { clientDatabase.settingsDatabase.getAccessToken() }

    /**
     * Retrieve stored refresh token
     */
    suspend fun getStoredRefreshToken(): String? =
        coroutineScope { clientDatabase.settingsDatabase.getRefreshToken() }

    /**
     * Refresh stored tokens
     */
    suspend fun refreshTokens(): AuthResponse? {
        val refresh = getStoredRefreshToken()

        if (refresh?.isNotEmpty() == true) {
            val response = serverHandler.userClient.refreshTokens(refresh)

            response
                .onSuccess { authResponse ->
                    AppLogger.m("$TAG:refreshTokens", "SUCCESS")
                    return authResponse
                }

            // If we make it this far then we know that wasn't successful
            if (serverHandler.userClient.login()) {
                return AuthResponse("", getStoredAccessToken() ?: "", getStoredRefreshToken() ?: "")
            }
        } else {
            if (serverHandler.userClient.login()) {
                return AuthResponse("", getStoredAccessToken() ?: "", getStoredRefreshToken() ?: "")
            }
        }

        return null
    }

    suspend fun getSettings(): UserSettings? {
        return coroutineScope {
            val result = clientDatabase.settingsDatabase.getSettings()

            if (result != null) {
                // Update internal values
                serverHandler.setUrl(result.serverUrl)
                autoSync = result.autoSync
                
                result
            } else null
        }
    }

    fun saveSettings(userSettings: UserSettings) {
        scope.launch {
            clientDatabase.settingsDatabase.saveSettings(userSettings)
        }
    }
    
    fun updateServerUrl(url: String) = serverHandler.updateServerUrl(url)

    suspend fun checkServerConnection(): Boolean = serverHandler.checkServerConnection()

    suspend fun checkNewServerConnection(url: String): Boolean = serverHandler.checkNewServerConnection(url)

    /**
     * Try to log in to server with username and password
     */
    suspend fun loginToServer(user: String, pass: String): Boolean {
        val result = serverHandler.login(user, pass)

        if (result) {
            // Save username and password
            updateStoredUsername(user)
            updateStoredPassword(pass)

            if (autoSync) syncData()

            AppLogger.m("$TAG:LoginToServer", SimpleError.SUCCESS)
            return true
        } else {
            AppLogger.i("$TAG:LoginToServer", SimpleError.FAIL)
            return false
        }
    }

    suspend fun loginCheck(): Boolean {
        return if (serverHandler.checkServerConnection()) {
            // Get stored values
            val username = getStoredUsername() ?: ""
            val password = getStoredPassword() ?: ""

            // Check if the essential values are empty
            if (username.isEmpty() || password.isEmpty()) { false }
            // Check credentials with server
            else if (serverHandler.userClient.checkCredentials(username, password)) { true }
            // else we've failed
            else { false }
        } else false
    }

    // TESTING
    // ----------------------------------------------------------------------------------------------------------------------------

    fun generateMockDbData() {
        scope.launch {
            val (customerData, ticketData, userData) = generateMockData(400)
            customerDataHandler.addAll(customerData)
            ticketDataHandler.addAll(ticketData)
            userDataHandler.addAll(userData)
            inventoryDataHandler.addAll(generateRandomInventoryItems(120))
        }
    }

    fun testPdf() {
        makeTicketInvoicePdf(
            filePath = "",
            fileName = "SampleTest.pdf",
            customerInfo = CustomerInfo(
                name = PersonName("Eric", "Garrison"),
                address = Address("134 Maple Dr", "Six Mile Run", "PA", "16679"),
                contactInfo = ContactInfo(listOf("Garrison@gmail.com"), listOf("8145367890"))
            ),
            ticketInfo = TicketInfo(
                identifier = "7865875",
                assignedUser = "",
                device = Device("Samsung", "Galaxy S7"),
                invoice = Invoice(
                    invoiceNumber = 123435678,
                    charges = listOf(
                        Charge(
                            type = ChargeType.HOURS,
                            amount = 3,
                            name = "Repair",
                            cost = 4500,
                            description = "Made repair done"
                        ),
                        Charge(
                            type = ChargeType.INVENTORY,
                            amount = 1,
                            name = "Solder",
                            cost = 1340,
                            description = "For Soldering"
                        ),
                        Charge(
                            type = ChargeType.INVENTORY,
                            amount = 1,
                            name = "Flux",
                            cost = 225,
                            description = "For Solder"
                        )
                    )
                )
            )
        )
    }
}
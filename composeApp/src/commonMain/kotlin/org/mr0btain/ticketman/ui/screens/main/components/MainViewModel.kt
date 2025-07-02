package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.components.data.components.invoice.makeTicketInvoicePdf
import org.mr0btain.ticketman.components.data.components.settings.UserSettings
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.generateMockData
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.components.network.ServerHandler
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.generateRandomInventoryItems
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.CustomerDataHandler
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.InventoryDataHandler
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.TicketDataHandler
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.UserDataHandler
import java.util.UUID

// TODO: Setup log in button and log in status

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app
 */

class MainViewModel(
    private val fileManager: FileManager,
    private val localDatabase: ClientDatabase,
    val snackBarHost: SnackbarHostState
) : ViewModel() {
    private val serverHandler = ServerHandler(getHttpEngine(), this)

    companion object {
        private const val TAG = "MainViewModel"
    }

    // TODO: Add a way to check if we have network connection, and once we do have it run a check and sync
    fun init() {
        viewModelScope.launch {
            // Pull settings from database
            getSettingsFromDatabase()
            syncDatabaseWithServer()
        }
    }

    // Platform the app is running on
    val platform = getPlatform().name

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }

    private val _showLogin: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    /**
     * Update whether we should show the login popup or not
     */
    fun updateShowLogin(value: Boolean) {
        _showLogin.value = value
    }

    /** Data Handlers. Mostly just to organise the mess in the view model. Probably not the best way to do it, but it looks much, much better */
    // -----------------------------------------------------------------------------------------------------------------------
    // TODO: Setup access level stuff
    // TODO: Should probably set it up so that we only get regular data for everyone else unless the user is Admin then pull sensitive data.

    val userDataHandler = UserDataHandler(
        localDatabaseClient = localDatabase.userDatabase,
        serverDatabaseClient = serverHandler.databaseClient.userClient
    )

    // Inventory handler
    val inventoryDataHandler = InventoryDataHandler(
        localDatabaseClient = localDatabase.inventoryDatabase,
        serverDatabaseClient = serverHandler.databaseClient.inventoryClient
    )

    // Ticket handler
    val ticketDataHandler = TicketDataHandler(
        localDatabaseClient = localDatabase.ticketDatabase,
        serverDatabaseClient = serverHandler.databaseClient.ticketClient
    )

    // Customer handler
    val customerDataHandler = CustomerDataHandler(
        localDatabaseClient = localDatabase.customerDatabase,
        serverDatabaseClient = serverHandler.databaseClient.customerClient
    )

    // TODO: User needs fleshed out
    private fun populateDataFromLocalDatabases() {
        viewModelScope.launch {
            customerDataHandler.populateListFromDatabase()
            ticketDataHandler.populateListFromDatabase()
            inventoryDataHandler.populateListFromDatabase()
            userDataHandler.populateListFromDatabase()
        }
    }

    /**
     * Syncs all data between client and server after checking connection with server
     */
    suspend fun syncDatabaseWithServer() {
        if (loginCheck()) {
            // Sync Data
            customerDataHandler.sync()
            ticketDataHandler.sync()
            inventoryDataHandler.sync()

            // Log Success
            AppLogger.m("$TAG:SyncDatabaseWithServer", "SUCCESS")
        }
    }

    /** Settings section */
    // ----------------------------------------------------------------------------------------------------------------------

    private val _darkMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val darkMode = _darkMode.asStateFlow()

    private val _serverAddress: MutableStateFlow<String> = MutableStateFlow("")
    val serverAddress = _serverAddress.asStateFlow()

    private val _scrollBarsOnBottom: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnBottom = _scrollBarsOnBottom.asStateFlow()

    private val _scrollBarsOnRight: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val scrollBarsOnRight = _scrollBarsOnRight.asStateFlow()

    private val _autoSyncDatabase: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val autoSyncDatabase = _autoSyncDatabase.asStateFlow()

    // Pulling it all at once this way saves us from a real hard hitch in the ui when we change these
    suspend fun getSettingsFromDatabase() {
        coroutineScope {
            val result = localDatabase.settingsDatabase.getSettings()

            if (result != null) {
                // Set state values
                _darkMode.value = result.darkMode
                _serverAddress.value = result.serverUrl
                _scrollBarsOnRight.value = result.scrollBarsOnRight
                _scrollBarsOnBottom.value = result.scrollBarsOnBottom
                _autoSyncDatabase.value = result.autoSync

                // update urls in server clients
                serverHandler.setUrl(result.serverUrl)
            }
        }
    }

    fun saveSettingsToDatabase() {
        viewModelScope.launch {
            localDatabase.settingsDatabase.saveSettings(
                UserSettings(
                    darkMode = _darkMode.value,
                    serverUrl = _serverAddress.value,
                    autoSync = _autoSyncDatabase.value,
                    scrollBarsOnRight = _scrollBarsOnRight.value,
                    scrollBarsOnBottom = _scrollBarsOnBottom.value
                )
            )
        }
    }

    fun toggleAutoSync() {
        val value = !_autoSyncDatabase.value
        _autoSyncDatabase.value = value
        saveSettingsToDatabase()
    }

    fun toggleDarkMode() {
        val value = !_darkMode.value
        _darkMode.value = value
        saveSettingsToDatabase()
    }

    fun saveServerAddress(address: String) {
        // update value
        _serverAddress.value = address
        saveSettingsToDatabase()

        // Run a sync operation if auto sync is enabled
        if (_autoSyncDatabase.value) {
            viewModelScope.launch { syncDatabaseWithServer() }
        }
    }

    fun updateServerAddress(url: String) {
        saveServerAddress(url)
        serverHandler.updateServerUrl(url)
    }

    fun toggleScrollBarsOnBottom() {
        val value = !_scrollBarsOnBottom.value
        _scrollBarsOnBottom.value = value
        saveSettingsToDatabase()
    }

    fun toggleScrollBarsOnRight() {
        val value = !_scrollBarsOnRight.value
        _scrollBarsOnRight.value = value
        saveSettingsToDatabase()
    }

    /**
     * Check to see if an address is valid. Will have to set up a ping or something to actually check. for now just check text
     */
    fun checkWebAddress(text: String): Boolean {
        var isValid = false

        // basic placeholder check
        if (text.startsWith("http://") || text.startsWith("https://") && text.endsWith(".com")) isValid = true

        // Basic ip check to see if there is enough digits
        val ipcheck = text.filter { it.isDigit() }
        if (ipcheck.length >= 6) isValid = true

        return isValid
    }

    // Server Stuff
    // ----------------------------------------------------------------------------------------------------------------------------

    /**
     * update stored username
     */
    suspend fun updateStoredUsername(username: String) {
        coroutineScope { localDatabase.settingsDatabase.setUsername(username) }
    }

    /**
     * Update stored password
     */
    suspend fun updateStoredPassword(password: String) {
        coroutineScope { localDatabase.settingsDatabase.setPassword(password) }
    }

    /**
     * Update stored access and refresh tokens
     */
    suspend fun updateStoredAuthTokens(access: String, refresh: String) {
        coroutineScope {
            localDatabase.settingsDatabase.setAccessToken(access)
            localDatabase.settingsDatabase.setRefreshToken(refresh)
        }
    }

    /**
     * Retrieve stored username
     */
    suspend fun getStoredUsername(): String? =
        coroutineScope { localDatabase.settingsDatabase.getUsername() }

    /**
     * Retrieve stored access password
     */
    suspend fun getStoredPassword(): String? =
        coroutineScope { localDatabase.settingsDatabase.getPassword() }

    /**
     * Retrieve stored access token
     */
    suspend fun getStoredAccessToken(): String? =
        coroutineScope { localDatabase.settingsDatabase.getAccessToken() }

    /**
     * Retrieve stored refresh token
     */
    suspend fun getStoredRefreshToken(): String? =
        coroutineScope { localDatabase.settingsDatabase.getRefreshToken() }

    /**
     * Hits the server at the specific address and see if we get a response and updates urls if successful
     */
    suspend fun checkNewServerConnection(url: String): Boolean = serverHandler.checkNewServerConnection(url)

    /**
     * Hits the saved server to see if we get a response
     */
    suspend fun checkServerConnection(): Boolean = serverHandler.checkServerConnection()

    /**
     * Try to log in to server with username and password
     */
    suspend fun loginToServer(user: String, pass: String): Boolean {
        val result = serverHandler.login(user, pass)

        if (result) {
            // Save username and password
            updateStoredUsername(user)
            updateStoredPassword(pass)

            if (_autoSyncDatabase.value) syncDatabaseWithServer()

            AppLogger.m("$TAG:LoginToServer", SimpleError.SUCCESS)
            return true
        } else {
            AppLogger.i("$TAG:LoginToServer", SimpleError.FAIL)
            return false
        }
    }

    /**
     * Refresh stored tokens
     */
    suspend fun refreshTokens(): AuthResponse? {
        val refresh = getStoredRefreshToken()

        if (refresh?.isNotEmpty() == true && _serverAddress.value.isNotEmpty()) {
            val response = serverHandler.userClient.refreshTokens(refresh)

            response
                .onSuccess { authResponse ->
                    AppLogger.m("$TAG:refreshTokens", "SUCCESS")
                    return authResponse
                }

            // If we make it this far then we know that wasn't successful
            if (serverHandler.userClient.login()) {
                return AuthResponse(UUID.randomUUID().toString(), getStoredAccessToken() ?: "", getStoredRefreshToken() ?: "")
            }
        } else {
            if (serverHandler.userClient.login()) {
                return AuthResponse(UUID.randomUUID().toString(), getStoredAccessToken() ?: "", getStoredRefreshToken() ?: "")
            }
        }

        return null
    }

    suspend fun loginCheck(): Boolean {
        return if (_serverAddress.value.isNotBlank() && checkServerConnection()) {
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
        viewModelScope.launch {
            val (customerData, ticketData, userData) = generateMockData(400)
            customerDataHandler.addAll(customerData)
            ticketDataHandler.addAll(ticketData)
            userDataHandler.addAll(userData)
            inventoryDataHandler.addAll(generateRandomInventoryItems(120))
        }
    }

    fun testPdf() {
        makeTicketInvoicePdf(
            filePath = FileKit.filesDir.path,
            fileName = "SampleTest.pdf",
            customerInfo = CustomerInfo(
                name = PersonName("Eric", "Garrison"),
                address = Address("134 Maple Dr", "Six Mile Run", "PA", "16679"),
                contactInfo = ContactInfo(listOf("Garrison@gmail.com"), listOf("8145367890"))
            ),
            ticketInfo = TicketInfo(
                identifier = "7865875",
                assignedUser = UUID.randomUUID().toString(),
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
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
import org.mr0btain.ticketman.data.MainDataItem
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
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.MainDataItemHandler
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

    // Platform the app is running on
    val platform = getPlatform().name

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
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

    // filtering only number
    fun filterNumbers(text: String): String = text.filter {
        it.isDigit()
    }

    // Check if text is a number
    fun checkNumber(text: String): Boolean {
        val value = text.toDoubleOrNull() ?: return false
        return value >= 0
    }

    /** User Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // TODO: User handler

    // TODO: Setup access level stuff
    // TODO: Should probably set it up so that we only get regular data for everyone else unless the user is Admin then pull sensitive data.
    // User stuff
    private val _user: MutableStateFlow<User> = MutableStateFlow(User())
    val user = _user.asStateFlow()

    // Employee list
    private val _userList: MutableStateFlow<List<User>> = MutableStateFlow(listOf())
    val userList = _userList.asStateFlow()

    // To update the user id
    fun updateOrAddUser(user: User) {
        viewModelScope.launch {
            // Check if item exists or not, if not then add it
            if (_userList.value.firstOrNull { it.identifier == user.identifier } != null) {
                // Update data in viewmodel
                // Get index of the item
                val index = _userList.value.indexOfFirst { it.identifier == user.identifier }

                // update list with new data
                _userList.value = _userList.value.toMutableList().also { it[index] = user }

                // Update database
                if (!localDatabase.userDatabase.update(user)) {
                    AppLogger.m("$TAG:updateOrAddUser", "Error updating user in database")
                    snackBarHost.showSnackbar("Error updating user!")
                }
            } else {
                // Else add new item to view model
                _userList.value = _userList.value + user

                // Add to database
                if (!localDatabase.userDatabase.add(user)) {
                    AppLogger.m("$TAG:updateOrAddUser", "Error adding user to database")
                }
            }
        }
    }
    /** Inventory Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Inventory handler
    val inventoryHandler = MainDataItemHandler<InventoryItem>(
        tag = "InventoryItemHandler",
        viewModel = this,
        localDatabaseClient = localDatabase.inventoryDatabase,
        serverClient = serverHandler.databaseClient.inventoryClient
    )

    // Inventory list
    private val _inventoryList: MutableStateFlow<List<InventoryItem>> = MutableStateFlow(listOf())
    val inventoryList = _inventoryList.asStateFlow()

    // Inventory selected item id
    private val _selectedInventoryId: MutableStateFlow<String> = MutableStateFlow("")
    val selectedInventoryId = _selectedInventoryId.asStateFlow()

    fun changeSelectedInventoryId(identifier: String) {
        _selectedInventoryId.value = identifier
    }

    /** Ticket Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Ticket handler
    val ticketHandler = MainDataItemHandler<TicketInfo>(
        tag = "TicketHandler",
        viewModel = this,
        localDatabaseClient = localDatabase.ticketDatabase,
        serverHandler.databaseClient.ticketClient
    )

    // Ticket data list
    private val _ticketDataList: MutableStateFlow<List<TicketInfo>> = MutableStateFlow(listOf())
    val ticketDataList = _ticketDataList.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketId: MutableStateFlow<String> = MutableStateFlow("")
    val selectedTicketId = _selectedTicketId.asStateFlow()

    // To update selected ticket
    fun changeSelectedTicketId(identifier: String) {
        _selectedTicketId.value = identifier
    }

    // Add or update ticket info if it exists
    fun updateOrAddTicket(data: TicketInfo) {
        viewModelScope.launch {
            // Check if ticket exists or not, if not then add it
            if (_ticketDataList.value.map { it.identifier }.contains(data.identifier)) {
                // Update data in viewmodel
                // Get index of the item
                val index = _ticketDataList.value.indexOfFirst { it.identifier == data.identifier }

                // update list with new data
                _ticketDataList.value = _ticketDataList.value.toMutableList().also { it[index] = data }

                // Update database
                if (!localDatabase.ticketDatabase.update(data)) {
                    AppLogger.m("$TAG:updateOrAddTicket", "Error updating ticket in database")
                    snackBarHost.showSnackbar("Error updating ticket!")
                }
            } else {
                // Else add new ticket to view model
                _ticketDataList.value = _ticketDataList.value + data

                // Add to database
                if (!localDatabase.ticketDatabase.add(data)) {
                    AppLogger.m("$TAG:updateOrAddTicket", "Error adding ticket to database")
                    snackBarHost.showSnackbar("Error adding ticket!")
                }
            }

            _selectedTicketId.value = data.identifier
        }
    }

    /** Customer related things */
    // ----------------------------------------------------------------------------------------------------------------------------

    // Customer handler
    val customerHandler = MainDataItemHandler<CustomerData>(
        tag = "CustomerHandler",
        viewModel = this,
        localDatabaseClient = localDatabase.customerDatabase,
        serverClient = serverHandler.databaseClient.customerClient
    )

    // Customer data list
    private val _customerDataList: MutableStateFlow<List<CustomerData>> = MutableStateFlow(listOf())
    val customerDataList = _customerDataList.asStateFlow()

    // Selected Customer
    private val _selectedCustomerId: MutableStateFlow<String> = MutableStateFlow("")
    val selectedCustomerId = _selectedCustomerId.asStateFlow()

    // Call to change customer data
    fun changeSelectedCustomerId(identifier: String) {
        _selectedCustomerId.value = identifier
    }

    // Add or update customer info if it exists
    fun updateOrAddCustomer(data: CustomerData) {
        viewModelScope.launch {
            // Check if customer exists or not, if not then add it
            if (_customerDataList.value.firstOrNull { it.identifier == data.identifier } != null) {
                // Update data in viewmodel
                // Get index of the item
                val index = _customerDataList.value.indexOfFirst { it.info.identifier == data.info.identifier }

                // update list with new data
                _customerDataList.value = _customerDataList.value.toMutableList().also { it[index] = data }

                // Update database
                if (!localDatabase.customerDatabase.update(data)) {
                    AppLogger.m("$TAG:updateOrAddCustomer", "Error updating customer in database")
                    snackBarHost.showSnackbar("Error updating customer!")
                }
            } else {
                // Else add to list in view model
                _customerDataList.value = _customerDataList.value + data

                // Add to database
                if (!localDatabase.customerDatabase.add(data)) {
                    AppLogger.m("$TAG:updateOrAddCustomer", "Error adding customer to database")
                    snackBarHost.showSnackbar("Error adding customer!")
                }
            }

            // Set selected customer
            _selectedCustomerId.value = data.identifier
        }
    }

    // Local Database
    // --------------------------------------------------------------------------------------------------------------------------

    // TODO: User needs fleshed out
    fun pullDataFromLocalDatabase() {
        viewModelScope.launch {
            // Read data from db and update it in view model
            customerHandler.getAll()?.let { _customerDataList.value = it }
            ticketHandler.getAll()?.let { _ticketDataList.value = it }
            inventoryHandler.getAll()?.let { _inventoryList.value = it }

            //_user.value = userList.value.firstOrNull { it.accessLevel == AccessLevel.ADMIN } ?: User()
            //println("Selected User: ${_user.value}")
        }
    }

    // Server Stuff
    // ----------------------------------------------------------------------------------------------------------------------------

    /**
     * get all of an item from local database
     */
    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <reified T : MainDataItem> getAllFromLocalDatabase(): List<T>? {
        return coroutineScope {
            try {
                when (T::class) {
                    CustomerData::class -> localDatabase.customerDatabase.getAll() as? List<T>?
                    TicketInfo::class -> localDatabase.ticketDatabase.getAll() as? List<T>?
                    InventoryItem::class -> localDatabase.inventoryDatabase.getAll() as? List<T>?
                    User::class -> localDatabase.userDatabase.getAll() as? List<T>?
                    else -> {
                        AppLogger.i("$TAG:GetAllFromDatabase", SimpleError.FAIL)
                        null
                    }
                }
            } catch (ex: Exception) {
                AppLogger.i("$TAG:GetAllFromDatabase", SimpleError.FAIL)
                null
            }
        }
    }

    /**
     * Get next id for item
     */
    private suspend inline fun <reified T : MainDataItem> getNextIdentifier(): String? {
        return coroutineScope {
            when (T::class) {
                CustomerData::class -> { localDatabase.customerDatabase.getNextId() }
                TicketInfo::class -> localDatabase.ticketDatabase.getNextId()
                InventoryItem::class -> localDatabase.inventoryDatabase.getNextId()
                User::class -> localDatabase.userDatabase.getNextId()
                else -> null
            }
        }
    }

    private val _showLogin: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    /**
     * Update whether we should show the login popup or not
     */
    fun updateShowLogin(value: Boolean) {
        _showLogin.value = value
    }

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

        if (refresh?.isNotEmpty() == true) {
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


    /**
     * Syncs all data between client and server after checking connection with server
     */
    suspend fun syncDatabaseWithServer() {
        // Check server connection
        if (loginCheck()) {
            // Sync Data
            customerHandler.sync()
            ticketHandler.sync()
            inventoryHandler.sync()

            // Log Success
            AppLogger.m("$TAG:SyncDatabaseWithServer", "SUCCESS")
        }
    }

    suspend fun loginCheck(): Boolean {
        return if (checkServerConnection()) {
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

    fun bulkAddCustomers(data: List<CustomerData>) { _customerDataList.value = _customerDataList.value + data }

    fun bulkAddTickets(data: List<TicketInfo>) { _ticketDataList.value = _ticketDataList.value + data }

    fun bulkAddInventory(data: List<InventoryItem>) { _inventoryList.value = _inventoryList.value + data }

    fun bulkAddUsers(data: List<User>) { _userList.value = _userList.value + data }

    // TODO: Add a way to check if we have network connection, and once we do have it run a check and sync
    fun init() {
        viewModelScope.launch {
            // Pull settings from database
            getSettingsFromDatabase()

            syncDatabaseWithServer()
        }
    }

    // TESTING
    // ----------------------------------------------------------------------------------------------------------------------------

    fun generateMockDbData() {
        val (customerdata, ticketData, userData) = generateMockData(400)

        //clientDatabase.ticketDatabase.addAll(ticketData)

        // Add the generated values to the database
        //clientDatabase.customerDatabase.addAll(customerdata)

        // Add all employees
        //clientDatabase.userDatabase.addAll(userData)

        // Generate and add inventory items
        //clientDatabase.inventoryDatabase.addAll(generateRandomInventoryItems(60))
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
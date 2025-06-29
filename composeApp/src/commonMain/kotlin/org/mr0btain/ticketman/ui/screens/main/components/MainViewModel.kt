package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.components.data.components.invoice.makeTicketInvoicePdf
import org.mr0btain.ticketman.components.data.datastore.createDataStore
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.generateMockData
import org.mr0btain.ticketman.generateRandomInventoryItems
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.ServerHandler
import java.util.UUID

// TODO: Setup error handling in here

/**
 * Main View model for the project.
 * Used to store data that needs to persist for the lifetime of the app
 */

class MainViewModel(
    private val fileManager: FileManager,
    private val clientDatabase: ClientDatabase,
    val snackBarHost: SnackbarHostState
) : ViewModel() {
    private val dataStore = createDataStore()

    private val serverHandler = ServerHandler(getHttpEngine(), this)

    companion object {
        const val TAG = "MainViewModel"

        private val AUTO_SYNC_DATABASE = booleanPreferencesKey("autoSyncDatabase")
        private val DARK_MODE = booleanPreferencesKey("appInDarkMode")
        private val SERVER_ADDRESS = stringPreferencesKey("serverAddress")
        private val SCROLL_BARS_ON_BOTTOM = booleanPreferencesKey("scrollBarsOnBottom")
        private val SCROLL_BARS_ON_RIGHT = booleanPreferencesKey("scrollBarsOnRight")
        private val USER_NAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val ACCESS_TOKEN = stringPreferencesKey("accessToken")
        private val REFRESH_TOKEN = stringPreferencesKey("refreshToken")
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
    fun getSettingsFromPreferences() {
        viewModelScope.launch {
            _darkMode.value = dataStore.data.map { preferences -> preferences[DARK_MODE] ?: false }.first()

            val url = dataStore.data.map { preferences -> preferences[SERVER_ADDRESS] ?: "" }.first()
            _serverAddress.value = url
            serverHandler.updateServerUrl(url)

            _scrollBarsOnBottom.value =
                dataStore.data.map { preferences -> preferences[SCROLL_BARS_ON_BOTTOM] ?: true }.first()
            _scrollBarsOnRight.value =
                dataStore.data.map { preferences -> preferences[SCROLL_BARS_ON_RIGHT] ?: true }.first()
        }
    }

    fun toggleAutoSync() {
        // Update the value
        val value = !_autoSyncDatabase.value

        _autoSyncDatabase.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[AUTO_SYNC_DATABASE] = value }
        }
    }

    fun toggleDarkMode() {
        // Update the value
        val value = !_darkMode.value

        _darkMode.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[DARK_MODE] = value }
        }
    }

    fun saveServerAddress(address: String) {
        // update value
        _serverAddress.value = address

        viewModelScope.launch {
            // Update datastore
            dataStore.edit { preferences -> preferences[SERVER_ADDRESS] = address }

            // Run a sync operation if auto sync is enabled
            if (_autoSyncDatabase.value) {
                syncDatabaseWithServer()
            }
        }
    }

    fun updateServerAddress(url: String) {
        saveServerAddress(url)
        serverHandler.updateServerUrl(url)
    }

    fun toggleScrollBarsOnBottom() {
        // Update the value
        val value = !_scrollBarsOnBottom.value

        _scrollBarsOnBottom.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[SCROLL_BARS_ON_BOTTOM] = value }
        }
    }

    fun toggleScrollBarsOnRight() {
        // Update the value
        val value = !_scrollBarsOnRight.value

        _scrollBarsOnRight.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[SCROLL_BARS_ON_RIGHT] = value }
        }
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
                if (!clientDatabase.userDatabase.update(user)) {
                    AppLogger.m("$TAG:updateOrAddUser", "Error updating user in database")
                    snackBarHost.showSnackbar("Error updating user!")
                }
            } else {
                // Else add new item to view model
                _userList.value = _userList.value + user

                // Add to database
                if (!clientDatabase.userDatabase.add(user)) {
                    AppLogger.m("$TAG:updateOrAddUser", "Error adding user to database")
                }
            }
        }
    }

    fun getUsersFromDatabase() {
        viewModelScope.launch {
            val result = clientDatabase.userDatabase.getAll()

            if (result != null) {
                _userList.value = result
            } else {
                AppLogger.m("$TAG:getUsersFromDatabase", "Error getting users from database")
                snackBarHost.showSnackbar("Error getting users!")
            }
        }
    }

    fun removeUser(identifier: String) {
        viewModelScope.launch {
            if (_userList.value.firstOrNull { it.identifier == identifier } != null) {
                // update list
                _userList.value = _userList.value.filter { it.identifier != identifier }

                // Update database
                if (!clientDatabase.userDatabase.remove(identifier)) {
                    AppLogger.m("$TAG:removeUser", "Error removing user from database")
                    snackBarHost.showSnackbar("Error removing user!")
                }
            } else {
                AppLogger.m("$TAG:removeUser", "Error removing user from database")
                snackBarHost.showSnackbar("Error removing user!")
            }
        }
    }

    fun getUserForId(identifier: String): User? {
        return userList.value.firstOrNull { it.identifier == identifier }
    }

    /** Inventory Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Inventory list
    private val _inventoryList: MutableStateFlow<List<InventoryItem>> = MutableStateFlow(listOf())
    val inventoryList = _inventoryList.asStateFlow()

    // Inventory selected item id
    private val _selectedInventoryId: MutableStateFlow<String> = MutableStateFlow("")
    val selectedInventoryId = _selectedInventoryId.asStateFlow()

    fun changeSelectedInventoryId(identifier: String) {
        _selectedInventoryId.value = identifier
    }

    fun getNextInventoryId(): String? {
        return clientDatabase.inventoryDatabase.getNextId()
    }

    fun getInventoryFromDatabase() {
        viewModelScope.launch {
            val result = clientDatabase.inventoryDatabase.getAll()

            if (result != null) {
                _inventoryList.value = result
            } else {
                AppLogger.m("$TAG:getInventoryFromDatabase", "Error getting items from database")
                snackBarHost.showSnackbar("Error getting inventory!")
            }
        }
    }

    fun updateOrAddInventoryItem(inventoryItem: InventoryItem) {
        viewModelScope.launch {
            // Check if item exists or not, if not then add it
            if (_inventoryList.value.firstOrNull { it.identifier == inventoryItem.identifier } != null) {
                // Update data in viewmodel
                // Get index of the item
                val index = _inventoryList.value.indexOfFirst { it.identifier == inventoryItem.identifier }

                // update list with new data
                _inventoryList.value = _inventoryList.value.toMutableList().also { it[index] = inventoryItem }

                // Update database
                if (!clientDatabase.inventoryDatabase.update(inventoryItem)) {
                    AppLogger.m("$TAG:updateOrAddInventoryItem", "Error updating item in database")
                    snackBarHost.showSnackbar("Error updating item!")
                }
            } else {
                // Else add new item to view model
                _inventoryList.value = _inventoryList.value + inventoryItem

                // Add to database
                if (!clientDatabase.inventoryDatabase.add(inventoryItem)) {
                    AppLogger.m("$TAG:updateOrAddInventoryItem", "Error adding item to database")
                    snackBarHost.showSnackbar("Error adding item!")
                }
            }

            _selectedInventoryId.value = inventoryItem.identifier
        }
    }

    fun removeInventoryItem(identifier: String) {
        viewModelScope.launch {
            // Check if item exists or not
            if (_inventoryList.value.firstOrNull { it.identifier == identifier } != null) {
                // update list
                _inventoryList.value = _inventoryList.value.filter { it.identifier != identifier }

                // Update database
                if (!clientDatabase.inventoryDatabase.remove(identifier)) {
                    AppLogger.m("$TAG:removeInventoryItem", "Error removing item from database")
                    snackBarHost.showSnackbar("Error removing item!")
                }
            } else {
                AppLogger.m("$TAG:removeInventoryItem", "Error removing item from database")
                snackBarHost.showSnackbar("Error removing item!")
            }
        }
    }

    fun getInventoryItemForId(identifier: String): InventoryItem? {
        val result = _inventoryList.value.firstOrNull { it.identifier == identifier }

        viewModelScope.launch {
            if (result == null) {
                AppLogger.m("$TAG:getInventoryItemForId", "Error getting item from database")
                snackBarHost.showSnackbar("Error getting item!")
            }
        }

        return result
    }

    /** Ticket Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // TODO: Add server update
    fun updateTicketInDatabase(data: TicketInfo): Boolean = clientDatabase.ticketDatabase.update(data)

    suspend fun addTicketToDatabase(data: TicketInfo): Boolean {
        val local = clientDatabase.ticketDatabase.add(data)
        val server = serverHandler.databaseClient.sendTickets(listOf(data))

        return local
    }

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

    fun getNextTicketId(): String? {
        return clientDatabase.ticketDatabase.getNextId()
    }

    fun getTicketsFromDatabase() {
        viewModelScope.launch {
            val result = clientDatabase.ticketDatabase.getAll()

            if (result != null) {
                _ticketDataList.value = result
            } else {
                AppLogger.m("$TAG:getTicketsFromDatabase", "Error getting tickets from database")
                snackBarHost.showSnackbar("Error getting tickets!")
            }
        }
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
                if (!clientDatabase.ticketDatabase.update(data)) {
                    AppLogger.m("$TAG:updateOrAddTicket", "Error updating ticket in database")
                    snackBarHost.showSnackbar("Error updating ticket!")
                }
            } else {
                // Else add new ticket to view model
                _ticketDataList.value = _ticketDataList.value + data

                // Add to database
                if (!clientDatabase.ticketDatabase.add(data)) {
                    AppLogger.m("$TAG:updateOrAddTicket", "Error adding ticket to database")
                    snackBarHost.showSnackbar("Error adding ticket!")
                }
            }

            _selectedTicketId.value = data.identifier
        }
    }

    fun removeTicket(identifier: String) {
        viewModelScope.launch {
            // Check if ticket exists or not
            if (_ticketDataList.value.firstOrNull { it.identifier == identifier } != null) {
                // update list
                _ticketDataList.value = _ticketDataList.value.filter { it.identifier != identifier }

                // Update database
                if (!clientDatabase.ticketDatabase.remove(identifier)) {
                    AppLogger.m("$TAG:removeTicket", "Error removing Ticket from database")
                    snackBarHost.showSnackbar("Error removing ticket!")
                }
            } else {
                AppLogger.m("$TAG:removeTicket", "Error removing ticket from database")
                snackBarHost.showSnackbar("Error removing ticket!")
            }
        }
    }

    fun getTicketForId(identifier: String): TicketInfo? {
        val result = _ticketDataList.value.firstOrNull { it.identifier == identifier }

        viewModelScope.launch {
            if (result == null) snackBarHost.showSnackbar("Error getting ticket!")
        }

        return result
    }

    /** Customer related things */
    // ----------------------------------------------------------------------------------------------------------------------------

    // TODO: Add server update
    fun updateCustomerInDatabase(data: CustomerData): Boolean = clientDatabase.customerDatabase.update(data)

    suspend fun addCustomerToDatabase(data: CustomerData): Boolean {
        val local = clientDatabase.customerDatabase.add(data)
        serverHandler.databaseClient.sendCustomers(listOf(data))

        return local
    }

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

    fun getNextCustomerId(): String? =
        clientDatabase.customerDatabase.getNextId()

    fun getCustomerForId(identifier: String): CustomerData? {
        val result = _customerDataList.value.firstOrNull() { it.identifier == identifier }

        if (result == null) viewModelScope.launch {
            AppLogger.m("$TAG:getCustomerForId", "Error getting customer from database")
            snackBarHost.showSnackbar("Could not find Customer!")
        }

        return result
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
                if (!clientDatabase.customerDatabase.update(data)) {
                    AppLogger.m("$TAG:updateOrAddCustomer", "Error updating customer in database")
                    snackBarHost.showSnackbar("Error updating customer!")
                }
            } else {
                // Else add to list in view model
                _customerDataList.value = _customerDataList.value + data

                // Add to database
                if (!clientDatabase.customerDatabase.add(data)) {
                    AppLogger.m("$TAG:updateOrAddCustomer", "Error adding customer to database")
                    snackBarHost.showSnackbar("Error adding customer!")
                }
            }

            // Set selected customer
            _selectedCustomerId.value = data.identifier
        }
    }

    // To update customer data list
    fun getCustomers() {
        viewModelScope.launch {
            val result = clientDatabase.customerDatabase.getAll()

            if (result != null) {
                _customerDataList.value = result
            } else {
                AppLogger.m("$TAG:getCustomers", "Error getting customers from database")
                snackBarHost.showSnackbar("Error getting customers!")
            }
        }
    }

    fun removeCustomer(identifier: String) {
        viewModelScope.launch {
            // Check if customer exists or not
            if (_customerDataList.value.map { it.identifier }.contains(identifier)) {
                // update list
                _customerDataList.value = _customerDataList.value.filter { it.identifier != identifier }

                // Update database
                if (!clientDatabase.customerDatabase.remove(identifier)) {
                    AppLogger.m("$TAG:removeCustomer", "Error removing customer from database")
                    snackBarHost.showSnackbar("Error removing customer!")
                }
            } else {
                AppLogger.m("$TAG:removeCustomer", "Error removing customer from database")
                snackBarHost.showSnackbar("Error removing customer!")
            }
        }
    }

    // Local Database
    // --------------------------------------------------------------------------------------------------------------------------

    // TODO: User needs fleshed out
    fun pullDataFromLocalDatabase() {
        // Read data from db and update it in view model
        getCustomers()
        getTicketsFromDatabase()
        getUsersFromDatabase()
        getInventoryFromDatabase()
        _user.value = userList.value.firstOrNull { it.accessLevel == AccessLevel.ADMIN } ?: User()
        println("Selected User: ${_user.value}")
    }

    // Server Stuff
    // ----------------------------------------------------------------------------------------------------------------------------

    // TODO: Add server update
    fun updateItemInDatabase(data: MainDataItem) {
        when (data) {
            is CustomerData -> {
                if (clientDatabase.customerDatabase.update(data)) {
                    AppLogger.m("${TAG}:UpdateItemInDatabase:CustomerData", "SUCCESS")
                } else AppLogger.i("${TAG}:UpdateItemInDatabase:CustomerData", "FAIL")
            }

            is TicketInfo -> {
                if (clientDatabase.ticketDatabase.update(data)) {
                    AppLogger.m("${TAG}:UpdateItemInDatabase:TicketInfo", "SUCCESS")
                } else AppLogger.i("${TAG}:UpdateItemInDatabase:TicketInfo", "FAIL")
            }
            is InventoryItem -> {
                if (clientDatabase.inventoryDatabase.update(data)) {
                    AppLogger.m("${TAG}:UpdateItemInDatabase:InventoryItem", "SUCCESS")
                } else AppLogger.i("${TAG}:UpdateItemInDatabase:InventoryItem", "FAIL")
            }
        }
    }

    suspend fun addItemToDatabase(data: MainDataItem){
        when (data) {
            is CustomerData -> {
                if (clientDatabase.customerDatabase.add(data)) {
                    AppLogger.m("${TAG}:AddItemToDatabase:Local:CustomerData", "SUCCESS")
                } else AppLogger.i("${TAG}:AddItemToDatabase:Local:CustomerData", "FAIL")

                serverHandler.databaseClient.sendCustomers(listOf(data))
            }

            is TicketInfo -> {
                if (clientDatabase.ticketDatabase.add(data)) {
                    AppLogger.m("${TAG}:AddItemToDatabase:Local:TicketInfo", "SUCCESS")
                } else AppLogger.i("${TAG}:AddItemToDatabase:Local:TicketInfo", "FAIL")

                serverHandler.databaseClient.sendTickets(listOf(data))
            }

            is InventoryItem -> {
                if (clientDatabase.inventoryDatabase.add(data)) {
                    AppLogger.m("${TAG}:AddItemToDatabase:Local:InventoryItem", "SUCCESS")
                } else AppLogger.i("${TAG}:AddItemToDatabase:Local:InventoryItem", "FAIL")

                serverHandler.databaseClient.sendInventory(listOf(data))
            }
        }
    }

    suspend fun removeItemFromDatabase(data: MainDataItem) {
        when (data) {
            is CustomerData -> {
                if (clientDatabase.customerDatabase.remove(data.identifier)) {
                    AppLogger.m("${TAG}:AddItemToDatabase:Local:CustomerData", "SUCCESS")
                } else AppLogger.i("${TAG}:AddItemToDatabase:Local:CustomerData", "FAIL")

                serverHandler.databaseClient.removeCustomer(data.identifier)
            }

            is TicketInfo -> {
                if (clientDatabase.ticketDatabase.remove(data.identifier)) {
                    AppLogger.m("${TAG}:AddItemToDatabase:Local:TicketInfo", "SUCCESS")
                } else AppLogger.i("${TAG}:AddItemToDatabase:Local:TicketInfo", "FAIL")

                serverHandler.databaseClient.removeTicket(data.identifier)
            }

            is InventoryItem -> {
                if (clientDatabase.inventoryDatabase.remove(data.identifier)) {
                    AppLogger.m("${TAG}:AddItemToDatabase:Local:InventoryItem", "SUCCESS")
                } else AppLogger.i("${TAG}:AddItemToDatabase:Local:InventoryItem", "FAIL")

                serverHandler.databaseClient.removeInventoryItem(data.identifier)
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
    fun updateStoredUsername(username: String) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[USER_NAME] = username }
        }
    }

    /**
     * Update stored password
     */
    fun updateStoredPassword(password: String) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[PASSWORD] = password }
        }
    }

    /**
     * Update stored access and refresh tokens
     */
    fun updateStoredAuthTokens(access: String, refresh: String) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[ACCESS_TOKEN] = access }
            dataStore.edit { preferences -> preferences[REFRESH_TOKEN] = refresh }
        }
    }

    /**
     * Retrieve stored username
     */
    suspend fun getStoredUsername(): String =
        dataStore.data.map { preferences -> preferences[USER_NAME] ?: "" }.first()

    // TODO: Make this encrypt on save and decrypt here
    /**
     * Retrieve stored access password
     */
    suspend fun getStoredPassword(): String =
        dataStore.data.map { preferences -> preferences[PASSWORD] ?: "" }.first()

    /**
     * Retrieve stored access token
     */
    suspend fun getStoredAccessToken(): String =
        dataStore.data.map { preferences -> preferences[ACCESS_TOKEN] ?: "" }.first()

    /**
     * Retrieve stored refresh token
     */
    suspend fun getStoredRefreshToken(): String =
        dataStore.data.map { preferences -> preferences[REFRESH_TOKEN] ?: "" }.first()

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

            AppLogger.m("$TAG:LoginToServer", "SUCCESS")
            return true
        } else {
            AppLogger.i("$TAG:LoginToServer", "SUCCESS")
            return false
        }
    }

    /**
     * Refresh stored tokens
     */
    suspend fun refreshTokens(): AuthResponse? {
        val refresh = getStoredRefreshToken()

        if (refresh.isNotEmpty()) {
            val response = serverHandler.userClient.refreshTokens(refresh)

            response
                .onSuccess { authResponse ->
                    AppLogger.m("$TAG:refreshTokens", "SUCCESS")
                    return authResponse
                }

            // If we make it this far then we know that wasn't successful so try getting both
            if (serverHandler.userClient.login()) {
                return AuthResponse(UUID.randomUUID(), getStoredAccessToken(), getStoredRefreshToken())
            }
        } else {
            if (serverHandler.userClient.login()) {
                return AuthResponse(UUID.randomUUID(), getStoredAccessToken(), getStoredRefreshToken())
            }
        }

        return null
    }

    /**
     * Syncs all data between client and server after checking connection with server
     */
    suspend fun syncDatabaseWithServer() {
        // Check server connection
        if (checkServerConnection()) {
            // Sync Customers
            serverHandler.syncCustomers(_customerDataList.value).let { data ->
                _customerDataList.value = _customerDataList.value + data
            }

            // Sync tickets
            serverHandler.syncTickets(_ticketDataList.value).let { data ->
                _ticketDataList.value = _ticketDataList.value + data
            }

            // Sync Inventory
            serverHandler.syncInventory(_inventoryList.value).let { data ->
                _inventoryList.value = _inventoryList.value + data
            }

            // Log Success
            AppLogger.m("$TAG:SyncDatabaseWithServer", "SUCCESS")
        }
    }

    init {
        viewModelScope.launch {
            if (getStoredUsername().isEmpty() || getStoredPassword().isEmpty()) {
                _showLogin.value = true
            }
        }
    }

    // TESTING
    // ----------------------------------------------------------------------------------------------------------------------------

    fun generateMockDbData() {
        val (customerdata, ticketData, userData) = generateMockData(400)

        clientDatabase.ticketDatabase.addAll(ticketData)

        // Add the generated values to the database
        clientDatabase.customerDatabase.addAll(customerdata)

        // Add all employees
        clientDatabase.userDatabase.addAll(userData)

        // Generate and add inventory items
        clientDatabase.inventoryDatabase.addAll(generateRandomInventoryItems(60))
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
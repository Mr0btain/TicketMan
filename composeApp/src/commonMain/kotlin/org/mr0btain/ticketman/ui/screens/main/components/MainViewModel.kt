package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
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
import org.mr0btain.ticketman.data.employee.AccessLevel
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.components.data.components.invoice.makeTicketInvoicePdf
import org.mr0btain.ticketman.components.data.datastore.createDataStore
import org.mr0btain.ticketman.components.database.ClientDatabase
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.components.network.ServerDatabaseClient
import org.mr0btain.ticketman.components.network.createHttpClient
import org.mr0btain.ticketman.components.network.getHttpEngine
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.generateMockData
import org.mr0btain.ticketman.generateRandomInventoryItems
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.onError
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.User
import org.mr0btain.ticketman.server.response.AuthResponse
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
    private val serverDatabaseClient = ServerDatabaseClient(createHttpClient(getHttpEngine(), this), dataStore)

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

            val server = dataStore.data.map { preferences -> preferences[SERVER_ADDRESS] ?: "" }.first()
            _serverAddress.value = server
            serverDatabaseClient.changeServerUrl(server)

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
            // Update url in database client
            serverDatabaseClient.changeServerUrl(address)

            // Update datastore
            dataStore.edit { preferences -> preferences[SERVER_ADDRESS] = address }

            // TODO: Don't know if we really need this after url change. maybe add an option to settings for auto sync
            // Run a sync operation
            syncDatabaseWithServer()
        }
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
     * Check to see if an address is valid. Will have to setup a ping or something to actually check. for now just check text
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


    fun removeUser(identifier: UUID) {
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

    fun getUserForId(identifier: UUID): User? {
        return userList.value.firstOrNull() { it.identifier == identifier }
    }

    /** Inventory Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Inventory list
    private val _inventoryList: MutableStateFlow<List<InventoryItem>> = MutableStateFlow(listOf())
    val inventoryList = _inventoryList.asStateFlow()

    // Inventory selected item id
    private val _selectedInventoryId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    val selectedInventoryId = _selectedInventoryId.asStateFlow()

    fun changeSelectedInventoryId(identifier: Int) {
        _selectedInventoryId.value = identifier
    }

    fun getNextInventoryId(): Int? {
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

    fun removeInventoryItem(identifier: Int) {
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

    fun getInventoryItemForId(identifier: Int): InventoryItem? {
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

    // Ticket data list
    private val _ticketDataList: MutableStateFlow<List<TicketInfo>> = MutableStateFlow(listOf())
    val ticketDataList = _ticketDataList.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    val selectedTicketId = _selectedTicketId.asStateFlow()

    // To update selected ticket
    fun changeSelectedTicketId(identifier: Int) {
        _selectedTicketId.value = identifier
    }

    fun getNextTicketId(): Int? {
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

    fun removeTicket(identifier: Int) {
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

    fun getTicketForId(id: Int): TicketInfo? {
        val result = _ticketDataList.value.firstOrNull { it.identifier == id }

        viewModelScope.launch {
            if (result == null) snackBarHost.showSnackbar("Error getting ticket!")
        }

        return result
    }

    /** Customer related things */
    // ----------------------------------------------------------------------------------------------------------------------------

    // Customer data list
    private val _customerDataList: MutableStateFlow<List<CustomerData>> = MutableStateFlow(listOf())
    val customerDataList = _customerDataList.asStateFlow()


    // Selected Customer
    private val _selectedCustomerId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    val selectedCustomerId = _selectedCustomerId.asStateFlow()

    // Call to change customer data
    fun changeSelectedCustomerId(identifier: Int) {
        _selectedCustomerId.value = identifier
    }

    fun getNextCustomerId(): Int? {
        return clientDatabase.customerDatabase.getNextId()
    }

    fun getCustomerForId(id: Int): CustomerData? {
        val result = _customerDataList.value.firstOrNull() { it.info.identifier == id }

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
            if (_customerDataList.value.firstOrNull { it.info.identifier == data.info.identifier } != null) {
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
            _selectedCustomerId.value = data.info.identifier
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

    fun removeCustomer(identifier: Int) {
        viewModelScope.launch {
            // Check if customer exists or not
            if (_customerDataList.value.map { it.info.identifier }.contains(identifier)) {
                // update list
                _customerDataList.value = _customerDataList.value.filter { it.info.identifier != identifier }

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

    // TODO: Employee needs fleshed out
    fun updateDataFromDb() {
        // Read data from db and update it in view model
        getCustomers()
        getTicketsFromDatabase()
        getUsersFromDatabase()
        getInventoryFromDatabase()
        _user.value = userList.value.firstOrNull { it.accessLevel == AccessLevel.ADMIN } ?: User()
        println("Selected User: ${_user.value}")
    }

    // Server Database
    // ----------------------------------------------------------------------------------------------------------------------------

    fun updateUsername(username: String) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[USER_NAME] = username }
        }
    }

    fun updatePassword(password: String) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[PASSWORD] = password }
        }
    }

    suspend fun refreshTokens(): AuthResponse? {
        val token = getRefreshToken()

        if (token.isEmpty()) {
            serverDatabaseClient.login()
                .onSuccess { response ->
                    dataStore.edit { preferences -> preferences[REFRESH_TOKEN] = response.refreshToken }
                    dataStore.edit { preferences -> preferences[ACCESS_TOKEN] = response.accessToken }

                    return response
                }
        } else {
            serverDatabaseClient.refreshTokens(token)
                .onSuccess { response ->
                    dataStore.edit { preferences -> preferences[REFRESH_TOKEN] = response.refreshToken }
                    dataStore.edit { preferences -> preferences[ACCESS_TOKEN] = response.accessToken }

                    return response
                }
        }

        return null
    }

    fun updateAccessToken(token: String) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[ACCESS_TOKEN] = token }
        }
    }

    suspend fun getUsername(): String =
        dataStore.data.map { preferences -> preferences[USER_NAME] ?: "" }.first()

    suspend fun getPassword(): String =
        dataStore.data.map { preferences -> preferences[PASSWORD] ?: "" }.first()

    suspend fun getAccessToken(): String =
        dataStore.data.map { preferences -> preferences[ACCESS_TOKEN] ?: "" }.first()

    suspend fun getRefreshToken(): String =
        dataStore.data.map { preferences -> preferences[REFRESH_TOKEN] ?: "" }.first()

    /**
     * Syncs all data between client and server after checking connection with server
     */
    suspend fun syncDatabaseWithServer() {
        // Check server connection
        if (serverDatabaseClient.checkServerConnection()) {
            syncCustomers()
            syncTickets()
            syncInventory()

            // Log Success
            AppLogger.m("$TAG:SyncDatabaseWithServer", "SUCCESS")
        }
    }

    /**
     * Sync customers with server database
     */
    suspend fun syncCustomers() {
        // Handle if our database is empty then no reason to sync just pull
        if (_customerDataList.value.isEmpty()) {
            serverDatabaseClient.getCustomers()
                .onSuccess { response ->
                    _customerDataList.value = response
                    clientDatabase.customerDatabase.addAll(response)

                    // Log Success
                    AppLogger.m("$TAG:GetCustomers", "SUCCESS")
                }.onError { error -> handleNetworkError("GetCustomers", error) }
        } else {
            // Else go through with sync
            serverDatabaseClient.syncCustomers(_customerDataList.value)
                .onSuccess { response ->
                    // Get requested data
                    val requestedData =
                        _customerDataList.value.filter { response.requested.contains(it.info.identifier) }

                    // Add server data to our database
                    _customerDataList.value = _customerDataList.value + response.data
                    clientDatabase.customerDatabase.addAll(response.data)

                    // Send back requested data
                    serverDatabaseClient.sendCustomers(requestedData)

                    // Log success
                    AppLogger.m("$TAG:SyncCustomers", "SUCCESS")
                }.onError { error -> handleNetworkError("SyncCustomers", error) }
        }
    }

    /**
     * Sync inventory with server database
     */
    suspend fun syncTickets() {
        // Handle if our database is empty then no reason to sync just pull
        if (_ticketDataList.value.isEmpty()) {
            serverDatabaseClient.getTickets()
                .onSuccess { response ->
                    _ticketDataList.value = response
                    clientDatabase.ticketDatabase.addAll(response)

                    // Log Success
                    AppLogger.m("$TAG:GetTickets", "SUCCESS")
                }.onError { error -> handleNetworkError("GetTickets", error) }
        } else {
            // Else go through with sync
            serverDatabaseClient.syncTickets(_ticketDataList.value)
                .onSuccess { response ->
                    // Get requested data
                    val requestedData =
                        _ticketDataList.value.filter { response.requested.contains(it.identifier) }

                    // Add server data to our database
                    _ticketDataList.value = _ticketDataList.value + response.data
                    clientDatabase.ticketDatabase.addAll(response.data)

                    // Send back requested data
                    serverDatabaseClient.sendTickets(requestedData)

                    // Log success
                    AppLogger.m("$TAG:SyncTickets", "SUCCESS")
                }.onError { error -> handleNetworkError("SyncTickets", error) }
        }
    }

    /**
     * Sync inventory with server database
     */
    suspend fun syncInventory() {
        // Handle if our database is empty then no reason to sync just pull
        if (_inventoryList.value.isEmpty()) {
            serverDatabaseClient.getInventory()
                .onSuccess { response ->
                    _inventoryList.value = response
                    clientDatabase.inventoryDatabase.addAll(response)

                    // Log Success
                    AppLogger.m("$TAG:GetInventory", "SUCCESS")
                }.onError { error -> handleNetworkError("GetInventory", error) }
        } else {
            // Else go through with sync
            serverDatabaseClient.syncInventory(_inventoryList.value)
                .onSuccess { response ->
                    // Handle requested data
                    if (response.requested.isNotEmpty()) {
                        // Get requested data
                        val requestedData =
                            _inventoryList.value.filter { response.requested.contains(it.identifier) }

                        // Send back requested data
                        serverDatabaseClient.sendInventory(requestedData)
                    }

                    // Handle received data
                    if (response.data.isNotEmpty()) {
                        // Add server data to our database
                        _inventoryList.value = _inventoryList.value + response.data
                        clientDatabase.inventoryDatabase.addAll(response.data)
                    }

                    // Log success
                    AppLogger.m("$TAG:SyncInventory", "SUCCESS")
                }.onError { error -> handleNetworkError("SyncInventory", error) }
        }
    }

    fun getDataFromServer(): Boolean {
        var success = false

        viewModelScope.launch {
            serverDatabaseClient.getCustomers()
                .onSuccess { data ->
                    _customerDataList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got customer data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }

            serverDatabaseClient.getTickets()
                .onSuccess { data ->
                    _ticketDataList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got ticket data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }

            serverDatabaseClient.getUsers()
                .onSuccess { data ->
                    _userList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got employee data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }

            serverDatabaseClient.getInventory()
                .onSuccess { data ->
                    _inventoryList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got inventory data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }
        }

        return success
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

    // TESTING
    // ----------------------------------------------------------------------------------------------------------------------------
    fun openTestFilePicker() {
        viewModelScope.launch {
            FileKit.openFilePicker()
        }
    }

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
                identifier = 7865875,
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
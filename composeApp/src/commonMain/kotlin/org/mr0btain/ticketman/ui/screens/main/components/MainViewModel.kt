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
import org.mr0btain.ticketman.data.employee.Employee
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.components.data.components.invoice.makeTicketInvoicePdf
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.components.network.DatabaseClient
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.LocalAppDatabase
import org.mr0btain.ticketman.generateMockData
import org.mr0btain.ticketman.generateRandomInventoryItems
import org.mr0btain.ticketman.getPlatform
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.onError
import org.mr0btain.ticketman.error.onSuccess

// TODO: Setup error handling in here

/**
* Main View model for the project.
* Used to store data that needs to persist for the lifetime of the app
 */

class MainViewModel(
    private val fileManager: FileManager,
    private val localAppDatabase: LocalAppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val databaseClient: DatabaseClient,
    val snackBarHost: SnackbarHostState
): ViewModel() {
    private companion object {
        const val TAG = "MainViewModel"

        val DARK_MODE = booleanPreferencesKey("appInDarkMode")
        val SERVER_ADDRESS = stringPreferencesKey("serverAddress")
        val SCROLL_BARS_ON_BOTTOM = booleanPreferencesKey("scrollBarsOnBottom")
        val SCROLL_BARS_ON_RIGHT = booleanPreferencesKey("scrollBarsOnRight")
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

    // Pulling it all at once this way saves us from a real hard hitch in the ui when we change these
    fun getSettingsFromPreferences() {
        viewModelScope.launch {
            _darkMode.value = dataStore.data.map { preferences -> preferences[DARK_MODE] ?: false }.first()

            val server = dataStore.data.map { preferences -> preferences[SERVER_ADDRESS] ?: "" }.first()
            _serverAddress.value = server
            databaseClient.changeServerUrl(server)

            _scrollBarsOnBottom.value = dataStore.data.map { preferences -> preferences[SCROLL_BARS_ON_BOTTOM] ?: true }.first()
            _scrollBarsOnRight.value = dataStore.data.map { preferences -> preferences[SCROLL_BARS_ON_RIGHT] ?: true }.first()
        }
    }

    fun toggleDarkMode(){
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
            databaseClient.changeServerUrl(address)
            dataStore.edit { preferences -> preferences[SERVER_ADDRESS] = address }
        }
    }

    fun toggleScrollBarsOnBottom(){
        // Update the value
        val value = !_scrollBarsOnBottom.value

        _scrollBarsOnBottom.value = value

        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[SCROLL_BARS_ON_BOTTOM] = value }
        }
    }

    fun toggleScrollBarsOnRight(){
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

    /** Employee Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // TODO: Setup access level stuff
    // TODO: Should probably set it up so that we only get regular data for everyone else unless the user is Admin then pull sensitive data.
    // Employee stuff
    private val _employee: MutableStateFlow<Employee> = MutableStateFlow(Employee())
    val employee = _employee.asStateFlow()

    // Employee list
    private val _employeeList: MutableStateFlow<List<Employee>> = MutableStateFlow(listOf())
    val employeeList = _employeeList.asStateFlow()

    // To update the user id
    fun updateOrAddEmployee(employee: Employee) {
        // Check if item exists or not, if not then add it
        if (_employeeList.value.firstOrNull { it.identifier == employee.identifier } != null) {
            // Update data in viewmodel
            // Get index of the item
            val index = _employeeList.value.indexOfFirst { it.identifier == employee.identifier }

            // update list with new data
            _employeeList.value = _employeeList.value.toMutableList().also { it[index] = employee }

            // Update database
            localAppDatabase.updateEmployee(employee)
        } else {
            // Else add new item to view model
            _employeeList.value = _employeeList.value + employee

            // Add to database
            localAppDatabase.addEmployee(employee)
        }
    }

    fun getEmployees() { _employeeList.value = localAppDatabase.getAllEmployees() }

    fun removeEmployee(id: Int) {
        // Check if employee exists or not
        if (_employeeList.value.firstOrNull { it.identifier == id } != null) {
            // update list
            _employeeList.value = _employeeList.value.filter { it.identifier != id }

            // Update database
            localAppDatabase.removeEmployee(id)
        } else {
            // TODO: We should trigger a warning or something
        }
    }

    fun getEmployeeForId(id: Int): Employee { return employeeList.value.firstOrNull() { it.identifier == id } ?: Employee() }

    /** Inventory Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Inventory list
    private val _inventoryList: MutableStateFlow<List<InventoryItem>> = MutableStateFlow(listOf())
    val inventoryList = _inventoryList.asStateFlow()

    // Inventory selected item id
    private val _selectedInventoryId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    val selectedInventoryId = _selectedInventoryId.asStateFlow()

    fun changeSelectedInventoryId(id: Int) { _selectedInventoryId.value = id }

    fun getNextInventoryId(): Int { return localAppDatabase.getNextInventoryId() }

    fun getInventory() { _inventoryList.value =  localAppDatabase.getInventory() }

    fun updateOrAddInventoryItem(inventoryItem: InventoryItem) {
        // Check if item exists or not, if not then add it
        if (_inventoryList.value.firstOrNull { it.identifier == inventoryItem.identifier } != null) {
            // Update data in viewmodel
            // Get index of the item
            val index = _inventoryList.value.indexOfFirst { it.identifier == inventoryItem.identifier }

            // update list with new data
            _inventoryList.value = _inventoryList.value.toMutableList().also { it[index] = inventoryItem }

            // Update database
            localAppDatabase.updateInventoryItem(inventoryItem)
        } else {
            // Else add new item to view model
            _inventoryList.value = _inventoryList.value + inventoryItem

            // Add to database
            localAppDatabase.addInventoryItem(inventoryItem)
        }

        _selectedInventoryId.value = inventoryItem.identifier
    }

    fun removeInventoryItem(id: Int) {
        // Check if item exists or not
        if (_inventoryList.value.firstOrNull { it.identifier == id } != null) {
            // update list
            _inventoryList.value = _inventoryList.value.filter { it.identifier != id }

            // Update database
            localAppDatabase.removeInventoryItem(id)
        } else {
            // TODO: We should trigger a warning or something
        }
    }

    fun getInventoryItemForId(id: Int): InventoryItem {
        return _inventoryList.value.firstOrNull { it.identifier == id } ?: InventoryItem()
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
    fun changeSelectedTicketId(id: Int) {
        _selectedTicketId.value = id
    }

    fun getNextTicketId(): Int { return localAppDatabase.getNextTicketId() }

    fun getTickets() { _ticketDataList.value = localAppDatabase.getAllTickets() }

    // Add or update ticket info if it exists
    fun updateOrAddTicket(data: TicketInfo) {
        // Check if ticket exists or not, if not then add it
        if (_ticketDataList.value.map { it.identifier }.contains(data.identifier)) {
            // Update data in viewmodel
            // Get index of the item
            val index = _ticketDataList.value.indexOfFirst { it.identifier == data.identifier }

            // update list with new data
            _ticketDataList.value = _ticketDataList.value.toMutableList().also { it[index] = data }

            // Update database
            localAppDatabase.updateTicket(data)
        } else {
            // Else add new ticket to view model
            _ticketDataList.value = _ticketDataList.value + data

            // Add to database
            localAppDatabase.addTicket(data)
        }

        _selectedTicketId.value = data.identifier
    }

    fun removeTicket(id: Int) {
        // Check if ticket exists or not
        if (_ticketDataList.value.firstOrNull { it.identifier == id } != null) {
            // update list
            _ticketDataList.value = _ticketDataList.value.filter { it.identifier != id }

            // Update database
            localAppDatabase.removeTicket(id)
        } else {
            // TODO: We should trigger a warning or something
        }
    }

    fun getTicketForId(id: Int): TicketInfo {
        return _ticketDataList.value.firstOrNull { it.identifier == id } ?: TicketInfo()
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
    fun changeSelectedCustomerId(id: Int) {
        _selectedCustomerId.value = id
    }

    fun getNextCustomerId(): Int { return localAppDatabase.getNextCustomerId() }

    fun getCustomerForId(id: Int): CustomerData {
        val result = _customerDataList.value.firstOrNull() { it.info.identifier == id }

        if (result == null) viewModelScope.launch {
            snackBarHost.showSnackbar("Could not find Customer!", withDismissAction = true)
        }

        return result ?: CustomerData()
    }

    // Add or update customer info if it exists
    fun updateOrAddCustomer(data: CustomerData) {
        // Check if customer exists or not, if not then add it
        if (_customerDataList.value.firstOrNull { it.info.identifier == data.info.identifier } != null) {
            // Update data in viewmodel
            // Get index of the item
            val index = _customerDataList.value.indexOfFirst { it.info.identifier == data.info.identifier }

            // update list with new data
            _customerDataList.value = _customerDataList.value.toMutableList().also { it[index] = data }

            // Update database
            localAppDatabase.updateCustomer(data)
        } else {
            // Else add to list in view model
            _customerDataList.value = _customerDataList.value + data

            // Add to database
            localAppDatabase.addCustomer(data)
        }

        // Set selected customer
        _selectedCustomerId.value = data.info.identifier
    }

    // To update customer data list
    fun getCustomers() { _customerDataList.value = localAppDatabase.getAllCustomers() }

    fun removeCustomer(id: Int) {
        // Check if customer exists or not
        if (_customerDataList.value.map { it.info.identifier }.contains(id)) {
            // update list
            _customerDataList.value = _customerDataList.value.filter { it.info.identifier != id }

            // Update database
            localAppDatabase.removeCustomer(id)
        } else {
            // TODO: We should trigger a warning or something
        }
    }

    // Local Database
    // --------------------------------------------------------------------------------------------------------------------------

    // To check and setup initial database if it doesn't exist
    fun setupDatabase() { localAppDatabase.checkAndCreateTables() }

    // TODO: Employee needs fleshed out
    fun updateDataFromDb() {
        // Read data from db and update it in view model
        getCustomers()
        getTickets()
        getEmployees()
        getInventory()
        _employee.value = employeeList.value.firstOrNull { it.accessLevel == AccessLevel.ADMIN } ?: Employee()
        println("Selected employee: ${_employee.value}")
    }

    // Server Database
    // ----------------------------------------------------------------------------------------------------------------------------

    fun getDataFromServer(): Boolean {
        var success = false

        viewModelScope.launch {
            databaseClient.getCustomerData()
                .onSuccess { data ->
                    _customerDataList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got customer data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }

            databaseClient.getTicketData()
                .onSuccess { data ->
                    _ticketDataList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got ticket data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }

            databaseClient.getEmployeeData()
                .onSuccess { data ->
                    _employeeList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got employee data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }

            databaseClient.getInventoryData()
                .onSuccess { data ->
                    _inventoryList.value = data
                    success = true
                    AppLogger.m("$TAG:GetDataFromServer", "SUCCESS got inventory data")
                }.onError { error -> handleNetworkError("GetDataFromServer", error) }
        }

        return success
    }

    // TODO: It seems okhttp throws stuff into log before we actually reach this and it never makes it to the logs
    private fun handleNetworkError(caller: String ,error: NetworkError) {
        when (error) {
            NetworkError.REQUEST_TIMEOUT -> { AppLogger.m("$TAG:$caller", error.name) }
            NetworkError.UNAUTHORIZED -> { AppLogger.i("$TAG:$caller", error.name) }
            NetworkError.CONFLICT -> { AppLogger.i("$TAG:$caller", error.name) }
            NetworkError.TOO_MANY_REQUESTS -> { AppLogger.i("$TAG:$caller", error.name) }
            NetworkError.NO_INTERNET -> { AppLogger.m("$TAG:$caller", error.name) }
            NetworkError.PAYLOAD_TOO_LARGE -> { AppLogger.i("$TAG:$caller", error.name) }
            NetworkError.SERVER_ERROR -> { AppLogger.i("$TAG:$caller", error.name) }
            NetworkError.SERIALIZATION -> { AppLogger.i("$TAG:$caller", error.name) }
            NetworkError.UNKNOWN -> { AppLogger.i("$TAG:$caller", error.name) }
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
        val (customerdata, ticketData, employeeData) = generateMockData(400)

        localAppDatabase.addAllTickets(ticketData)

        // Add the generated values to the database
        localAppDatabase.addAllCustomers(customerdata)

        // Add all employees
        localAppDatabase.addAllEmployees(employeeData)

        // Generate and add inventory items
        localAppDatabase.addAllInventory(generateRandomInventoryItems(60))
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
                assignedEmployee = 356247,
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
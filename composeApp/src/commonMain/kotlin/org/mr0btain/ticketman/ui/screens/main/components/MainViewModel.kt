package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.components.data.components.customer.CustomerData
import org.mr0btain.ticketman.components.data.components.employee.AccessLevel
import org.mr0btain.ticketman.components.data.components.employee.Employee
import org.mr0btain.ticketman.components.data.components.inventory.InventoryItem
import org.mr0btain.ticketman.components.data.components.ticket.TicketInfo
import org.mr0btain.ticketman.components.data.database.SqliteDb
import org.mr0btain.ticketman.components.data.generateMockData
import org.mr0btain.ticketman.components.data.generateRandomInventoryItems
import org.mr0btain.ticketman.getPlatform

// TODO: Setup error handling in here

/**
* Main View model for the project.
* Used to store data that needs to persist for the lifetime of the app
 */

class MainViewModel(
    private val fileManager: FileManager,
    private val database: SqliteDb,
    val snackBarHost: SnackbarHostState
): ViewModel() {
    private companion object {
        const val TAG = "MainViewModel"
    }
    // Todo Check if we even need this
    val platform = getPlatform().name

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
    var selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: Int) {
        _selectedMainTabIndex.value = update
    }

    /** Employee Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // TODO: Setup access level stuff
    // TODO: Should probably set it up so that we only get regular data for everyone else unless the user is Admin then pull sensitive data.
    // Employee stuff
    private val _employee: MutableStateFlow<Employee> = MutableStateFlow(Employee())
    var employee = _employee.asStateFlow()

    // Employee list
    private val _employeeList: MutableStateFlow<List<Employee>> = MutableStateFlow(listOf())
    var employeeList = _employeeList.asStateFlow()

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
            database.updateEmployee(employee)
        } else {
            // Else add new item to view model
            _employeeList.value = _employeeList.value + employee

            // Add to database
            database.addEmployee(employee)
        }
    }

    fun getEmployees() { _employeeList.value = database.getAllEmployees() }

    fun removeEmployee(id: Int) {
        // Check if employee exists or not
        if (_employeeList.value.firstOrNull { it.identifier == id } != null) {
            // update list
            _employeeList.value = _employeeList.value.filter { it.identifier != id }

            // Update database
            database.removeEmployee(id)
        } else {
            // TODO: We should trigger a warning or something
        }
    }

    fun getEmployeeForId(id: Int): Employee { return employeeList.value.firstOrNull() { it.identifier == id } ?: Employee() }

    /** Inventory Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Inventory list
    private val _inventoryList: MutableStateFlow<List<InventoryItem>> = MutableStateFlow(listOf())
    var inventoryList = _inventoryList.asStateFlow()

    // Inventory selected item id
    private val _selectedInventoryId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    var selectedInventoryId = _selectedInventoryId.asStateFlow()

    fun changeSelectedInventoryId(id: Int) { _selectedInventoryId.value = id }

    fun getNextInventoryId(): Int { return database.getNextInventoryId() }

    fun getInventory() { _inventoryList.value =  database.getInventory() }

    fun updateOrAddInventoryItem(inventoryItem: InventoryItem) {
        // Check if item exists or not, if not then add it
        if (_inventoryList.value.firstOrNull { it.identifier == inventoryItem.identifier } != null) {
            // Update data in viewmodel
            // Get index of the item
            val index = _inventoryList.value.indexOfFirst { it.identifier == inventoryItem.identifier }

            // update list with new data
            _inventoryList.value = _inventoryList.value.toMutableList().also { it[index] = inventoryItem }

            // Update database
            database.updateInventoryItem(inventoryItem)
        } else {
            // Else add new item to view model
            _inventoryList.value = _inventoryList.value + inventoryItem

            // Add to database
            database.addInventoryItem(inventoryItem)
        }

        _selectedInventoryId.value = inventoryItem.identifier
    }

    fun removeInventoryItem(id: Int) {
        // Check if item exists or not
        if (_inventoryList.value.firstOrNull { it.identifier == id } != null) {
            // update list
            _inventoryList.value = _inventoryList.value.filter { it.identifier != id }

            // Update database
            database.removeInventoryItem(id)
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
    var ticketDataList = _ticketDataList.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    var selectedTicketId = _selectedTicketId.asStateFlow()

    // To update selected ticket
    fun changeSelectedTicketId(id: Int) {
        _selectedTicketId.value = id
    }

    fun getNextTicketId(): Int { return database.getNextTicketId() }

    fun getTickets() { _ticketDataList.value = database.getAllTickets() }

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
            database.updateTicket(data)
        } else {
            // Else add new ticket to view model
            _ticketDataList.value = _ticketDataList.value + data

            // Add to database
            database.addTicket(data)
        }

        _selectedTicketId.value = data.identifier
    }

    fun removeTicket(id: Int) {
        // Check if ticket exists or not
        if (_ticketDataList.value.firstOrNull { it.identifier == id } != null) {
            // update list
            _ticketDataList.value = _ticketDataList.value.filter { it.identifier != id }

            // Update database
            database.removeTicket(id)
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
    var customerDataList = _customerDataList.asStateFlow()


    // Selected Customer
    private val _selectedCustomerId: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    var selectedCustomerId = _selectedCustomerId.asStateFlow()

    // Call to change customer data
    fun changeSelectedCustomerId(id: Int) {
        _selectedCustomerId.value = id
    }

    fun getNextCustomerId(): Int { return database.getNextCustomerId() }

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
            database.updateCustomer(data)
        } else {
            // Else add to list in view model
            _customerDataList.value = _customerDataList.value + data

            // Add to database
            database.addCustomer(data)
        }

        // Set selected customer
        _selectedCustomerId.value = data.info.identifier
    }

    // To update customer data list
    fun getCustomers() { _customerDataList.value = database.getAllCustomers() }

    fun removeCustomer(id: Int) {
        // Check if customer exists or not
        if (_customerDataList.value.map { it.info.identifier }.contains(id)) {
            // update list
            _customerDataList.value = _customerDataList.value.filter { it.info.identifier != id }

            // Update database
            database.removeCustomer(id)
        } else {
            // TODO: We should trigger a warning or something
        }
    }

    // Database
    // --------------------------------------------------------------------------------------------------------------------------

    // To check and setup initial database if it doesn't exist
    fun setupDatabase() { database.checkAndCreateTables() }

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

    // ----------------------------------------------------------------------------------------------------------------------------
    // TESTING

    fun openTestFilePicker() {
        viewModelScope.launch {
            FileKit.openFilePicker()
        }
    }

    fun generateMockDbData() {
        val (customerdata, ticketData, employeeData) = generateMockData(400)

        database.addAllTickets(ticketData)

        // Add the generated values to the database
        database.addAllCustomers(customerdata)

        // Add all employees
        database.addAllEmployees(employeeData)

        // Generate and add inventory items
        database.addAllInventory(generateRandomInventoryItems(60))
    }
}
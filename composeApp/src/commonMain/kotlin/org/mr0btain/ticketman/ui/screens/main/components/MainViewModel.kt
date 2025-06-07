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
import org.mr0btain.ticketman.components.data.components.AccessLevel
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.Employee
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.database.SqliteDb
import org.mr0btain.ticketman.components.data.generateMockData
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
    private val mainTag = "MainViewModel"
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
    fun changeEmployee(employee: Employee) {
        _employee.value = employee
    }

    fun changeEmployeeList(list: List<Employee>) {
        _employeeList.value = list
    }

    fun getAllEmployees(): List<Employee> { return database.getAllEmployees() }

    fun addEmployee(employee: Employee) { database.addEmployee(employee) }

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

    fun getEmployee(id: Int): Employee { return employeeList.value.firstOrNull() { it.identifier == id } ?: Employee() }

    /** Ticket Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Ticket data list
    private val _ticketDataList: MutableStateFlow<List<TicketInfo>> = MutableStateFlow(listOf())
    var ticketDataList = _ticketDataList.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketId: MutableStateFlow<Int> = MutableStateFlow(0)
    var selectedTicketId = _selectedTicketId.asStateFlow()

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
            // Else add new ticket
            database.addTicket(data)
        }
    }

    // To update selected ticket
    fun changeSelectedTicketId(id: Int) {
        _selectedTicketId.value = id
    }

    // To update ticket data list
    fun changeTicketDataList(list: List<TicketInfo>) {
        _ticketDataList.value = list
    }

    fun getTicketForId(id: Int): TicketInfo {
        return _ticketDataList.value.firstOrNull { it.identifier == id } ?: TicketInfo()
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

    /** Customer related things */
    // ----------------------------------------------------------------------------------------------------------------------------

    // Customer data list
    private val _customerDataList: MutableStateFlow<List<CustomerData>> = MutableStateFlow(listOf())
    var customerDataList = _customerDataList.asStateFlow()


    // Selected Customer
    private val _selectedCustomerId: MutableStateFlow<Int> = MutableStateFlow(0)
    var selectedCustomerId = _selectedCustomerId.asStateFlow()

    // Add or update customer info if it exists
    fun updateOrAddCustomer(data: CustomerData) {
        // Check if customer exists or not, if not then add it
        if (_customerDataList.value.map { it.info.identifier }.contains(data.info.identifier)) {
            // Update data in viewmodel
            // Get index of the item
            val index = _customerDataList.value.indexOfFirst { it.info.identifier == data.info.identifier }

            // update list with new data
            _customerDataList.value = _customerDataList.value.toMutableList().also { it[index] = data }

            // Update database
            database.updateCustomer(data)
        } else {
            // Else add new
            database.addCustomer(data)
        }
    }

    // Call to change customer data
    fun changeSelectedCustomerId(id: Int) {
        _selectedCustomerId.value = id
    }

    // To update customer data list
    fun changeCustomerDataList(list: List<CustomerData>) {
        _customerDataList.value = list
    }

    fun getCustomerData(id: Int): CustomerData {
        val result = _customerDataList.value.firstOrNull() { it.info.identifier == id }

        if (result == null) viewModelScope.launch {
            snackBarHost.showSnackbar("Could not find Customer!", withDismissAction = true)
        }

        return result ?: CustomerData()
    }

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

    fun getNextCustomerId(): Int { return database.getNextCustomerId() }

    fun getNextTicketId(): Int { return database.getNextTicketId() }

    // TODO: Employee needs fleshed out
    fun updateDataFromDb() {
        // Read data from db and update it in view model
        changeCustomerDataList(database.getAllCustomerData())
        changeTicketDataList(database.getAllTicketData())
        changeEmployeeList(database.getAllEmployees())
        changeEmployee(employeeList.value.firstOrNull { it.accessLevel == AccessLevel.ADMIN } ?: Employee())
    }

    // ----------------------------------------------------------------------------------------------------------------------------
    // TESTING

    fun openTestFilePicker() {
        viewModelScope.launch {
            FileKit.openFilePicker()
        }
    }

    fun generateMockDbData() {
        val (customerdata, ticketData, employeeData) = generateMockData(20)

        database.addAllTicketData(ticketData)

        // Add the generated values to the database
        database.addAllCustomerData(customerdata)

        // Add all employees
        database.addAllEmployees(employeeData)
    }
}
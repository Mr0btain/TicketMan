package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.FileManager
import org.mr0btain.ticketman.components.data.components.AccessLevel
import org.mr0btain.ticketman.components.data.components.Address
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.Employee
import org.mr0btain.ticketman.components.data.components.PersonName
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.database.SqliteDb
import org.mr0btain.ticketman.components.data.generateMockData
import org.mr0btain.ticketman.getPlatform

// TODO: Setup error handling in here

/**
Main View model for the project.
Used to store data that needs to persist for the lifetime of the app and be used anywhere
 */

class MainViewModel(
    private val fileManager: FileManager,
    private val database: SqliteDb
): ViewModel() {
    val topAppBarText = mutableStateOf("Welcome")
    val platform = getPlatform().name



    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<String> = MutableStateFlow("0")
    var selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: String) {
        _selectedMainTabIndex.value = update
    }



    /** Employee Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // TODO: Setup access level stuff
    // TODO: Should probably set it up so that we only get regular data for everyone else unless the user is Admin then pull sensitive data.
    // Employee stuff
    private val _employee: MutableStateFlow<Employee> = MutableStateFlow(Employee(identifier = "201"))
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

    /** Ticket Related Things */
    // -----------------------------------------------------------------------------------------------------------------------

    // Ticket data list
    private val _ticketDataList: MutableStateFlow<List<TicketInfo>> = MutableStateFlow(listOf())
    var ticketDataList = _ticketDataList.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketId: MutableStateFlow<String> = MutableStateFlow("")
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
    fun changeSelectedTicketId(id: String) {
        _selectedTicketId.value = id
    }

    // To update ticket data list
    fun changeTicketDataList(list: List<TicketInfo>) {
        _ticketDataList.value = list
    }

    fun getTicketForId(id: String): TicketInfo {
        return _ticketDataList.value.firstOrNull { it.identifier == id } ?: TicketInfo()
    }

    fun getTicketsForCustomer(customerId: String): List<TicketInfo> {
        return _ticketDataList.value.filter { it.customerId == customerId }
    }

    fun getTickets(tickets: List<String>): List<TicketInfo> {
        val result = mutableListOf<TicketInfo>()

        _ticketDataList.value.forEach {
            if (tickets.contains(it.identifier)) {
                result.add(it)
            }
        }

        return result
    }

    /** Customer related things */
    // ----------------------------------------------------------------------------------------------------------------------------

    // Customer data list
    private val _customerDataList: MutableStateFlow<List<CustomerData>> = MutableStateFlow(listOf())
    var customerDataList = _customerDataList.asStateFlow()


    // Selected Customer
    private val _selectedCustomerId: MutableStateFlow<String> = MutableStateFlow("")
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
    fun changeSelectedCustomerId(id: String) {
        _selectedCustomerId.value = id
    }

    // To update customer data list
    fun changeCustomerDataList(list: List<CustomerData>) {
        _customerDataList.value = list
    }

    fun getCustomerData(id: String): CustomerData {
        return _customerDataList.value.firstOrNull() { it.info.identifier == id } ?: CustomerData()
    }

    // Database
    // --------------------------------------------------------------------------------------------------------------------------

    // To check and setup initial database if it doesn't exist
    fun setupDatabase() { database.checkAndCreateTables() }

    fun getNextCustomerId(): String { return database.getNextCustomerId() }

    fun getNextTicketId(): String { return database.getNextTicketId() }

    fun updateCustomerName(id: String, data: PersonName) {
        database.updateCustomerName(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(info = it.info.copy(name = data)) else it
        }

        // update selected data as well
        _selectedCustomerId.value = id
    }

    fun updateCustomerAddress(id: String, data: Address) {
        database.updateCustomerAddress(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(info = it.info.copy(address = data)) else it
        }

        // update selected data as well
        _selectedCustomerId.value = id
    }

    fun updateCustomerContact(id: String, data: ContactInfo) {
        database.updateCustomerContact(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(info = it.info.copy(contactInfo = data)) else it
        }

        // update selected data as well
        _selectedCustomerId.value = id
    }

    fun updateCustomerTickets(id: String, data: List<String>) {
        database.updateCustomerTickets(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(tickets = data) else it
        }

        // update selected data as well
        _selectedCustomerId.value = id
    }

    fun updateCustomerNotes(id: String, data: List<Note>) {
        database.updateCustomerNotes(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(notes = data) else it
        }

        // update selected data as well
        _selectedCustomerId.value = id
    }

    fun addCustomer(data: CustomerData) {
        // Add to list
        _customerDataList.value = _customerDataList.value + data

        // Set selected
        _selectedCustomerId.value = data.info.identifier

        // Push to database
        database.addCustomer(data)
    }

    fun addTicket(data: TicketInfo) {
        // Add to list
        _ticketDataList.value = _ticketDataList.value + data

        // Set selected ticket
        _selectedTicketId.value = data.identifier

        // Push to database
        database.addTicket(data)
    }

    fun getAllEmployees(): List<Employee> {
        return database.getAllEmployees()
    }

    fun addEmployee(employee: Employee) {
        database.addEmployee(employee)
    }

    fun removeEmployee(employee: Employee) {
        database.removeEmployee(employee.identifier)
    }

    fun getEmployee(id: String): Employee {
        return employeeList.value.firstOrNull() { it.identifier == id } ?: Employee()
    }

    // TODO: Employee needs fleshed out
    fun updateDataFromDb() {
        // Read data from mock db and update it in view model
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

    fun checkIfTablesExist(): Boolean {
        return database.listTables().containsAll(listOf("customer", "tickets", "employee"))
    }

    fun generateMockDbData() {
        val (customerdata, ticketData, employeeData) = generateMockData(600)

        database.addAllTicketData(ticketData)

        // Add the generated values to the database
        database.addAllCustomerData(customerdata)

        // Add all employees
        database.addAllEmployees(employeeData)
    }

    fun getMockDataFromDb() {
        // Now lets read and set values in the viewmodel
        changeCustomerDataList(database.getAllCustomerData())
    }
}
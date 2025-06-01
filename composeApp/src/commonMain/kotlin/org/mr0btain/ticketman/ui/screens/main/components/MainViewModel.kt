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

    // User specific ticket data list
    private val _myTicketDataList: MutableStateFlow<List<String>> = MutableStateFlow(listOf())
    var myTicketDataList = _myTicketDataList.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketTabIndex: MutableStateFlow<String> = MutableStateFlow("0")
    var selectedTicketTabIndex = _selectedTicketTabIndex.asStateFlow()

    // Selected ticket tab index
    private val _selectedTicketInfo: MutableStateFlow<TicketInfo> = MutableStateFlow(TicketInfo())
    var selectedTicketInfo = _selectedTicketInfo.asStateFlow()

    // Selected Customer data index default to absurd number
    private val _selectedTicketIndex: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    var selectedTicketIndex = _selectedTicketIndex.asStateFlow()

    fun changeSelectedTicketIndex(index: Int) {
        _selectedTicketIndex.value = index
    }

    // To update selected ticket
    fun changeSelectedTicketInfo(ticket: TicketInfo) {
        _selectedTicketInfo.value = ticket
    }

    // To update ticket data list
    fun changeTicketDataList(list: List<TicketInfo>) {
        _ticketDataList.value = list
    }

    // To update the ticket tab index
    fun changeTicketTabIndex(update: String) {
        _selectedTicketTabIndex.value = update
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


    // Selected Customer data
    private val _selectedCustomerData: MutableStateFlow<CustomerData> = MutableStateFlow(CustomerData())
    var selectedCustomerData = _selectedCustomerData.asStateFlow()

    // Selected Customer data index default to absurd number
    private val _selectedCustomerDataIndex: MutableStateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)
    var selectedCustomerDataIndex = _selectedCustomerDataIndex.asStateFlow()

    fun changeSelectedCustomerIndex(index: Int) {
        _selectedCustomerDataIndex.value = index
    }

    // Call to change customer data
    fun changeSelectedCustomerData(customerData: CustomerData) {
        _selectedCustomerData.value = customerData
    }

    // To update customer data list
    fun changeCustomerDataList(list: List<CustomerData>) {
        _customerDataList.value = list
    }

    fun getCustomerData(id: String): CustomerData {
        return _customerDataList.value.firstOrNull() { it.info.identifier == id } ?: CustomerData()
    }

    // To get the index of an item
    fun getCustomerIndex(data: CustomerData): Int { return _customerDataList.value.indexOf(data) }

    // Get a list of all customer names
    fun getAllCustomerInfo(): List<CustomerInfo> {
        val result = mutableListOf<CustomerInfo>()

        _customerDataList.value.forEach { customerData ->
            result.add(customerData.info)
        }

        return result
    }

    // Database
    // --------------------------------------------------------------------------------------------------------------------------

    // To check and setup initial database if it doesnt exist
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
        _selectedCustomerData.value = _selectedCustomerData.value.copy(info = _selectedCustomerData.value.info.copy(name = data))
    }

    fun updateCustomerAddress(id: String, data: Address) {
        database.updateCustomerAddress(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(info = it.info.copy(address = data)) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(info = _selectedCustomerData.value.info.copy(address = data))
    }

    fun updateCustomerContact(id: String, data: ContactInfo) {
        database.updateCustomerContact(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(info = it.info.copy(contactInfo = data)) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(info = _selectedCustomerData.value.info.copy(contactInfo = data))
    }

    fun updateCustomerTickets(id: String, data: List<String>) {
        database.updateCustomerTickets(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(tickets = data) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(tickets = data)
    }

    fun updateCustomerNotes(id: String, data: List<Note>) {
        database.updateCustomerNotes(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.identifier == id) it.copy(notes = data) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(notes = data)
    }

    fun addCustomer(data: CustomerData) {
        // Add to list
        _customerDataList.value = _customerDataList.value + data

        // Set selected
        _selectedCustomerData.value = data

        // Set index
        _selectedCustomerDataIndex.value = getCustomerIndex(data)

        // Push to database
        database.addCustomer(data)
    }

    fun addTicket(data: TicketInfo) {
        // Add to list
        _ticketDataList.value = _ticketDataList.value + data

        // Set selected ticket
        _selectedTicketInfo.value = data

        // Set index
        // TODO

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
        return database.listTables().containsAll(listOf("customer", "tickets"))
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
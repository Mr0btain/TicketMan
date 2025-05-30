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
import org.mr0btain.ticketman.components.data.components.Address
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.CustomerName
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.Notes
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
    val topAppBarText = mutableStateOf("Main")
    val platform = getPlatform().name

    // User ID
    private val _employeeId: MutableStateFlow<String> = MutableStateFlow("201")
    var employeeId = _employeeId.asStateFlow()

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<String> = MutableStateFlow("0")
    var selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()

    // To update the main tab index
    fun changeMainTabIndex(update: String) {
        _selectedMainTabIndex.value = update
    }

    // To update the user id
    fun changeUserId(update: String) {
        _employeeId.value = update
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
            if (tickets.contains(it.idNum)) {
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

    // Call to change customer data
    fun changeSelectedCustomerData(customerData: CustomerData) {
        _selectedCustomerData.value = customerData
    }

    // To update customer data list
    fun changeCustomerDataList(list: List<CustomerData>) {
        _customerDataList.value = list
    }

    fun getCustomerData(id: String): CustomerData {
        return _customerDataList.value.first { it.info.id == id }
    }

    // Database
    // --------------------------------------------------------------------------------------------------------------------------

    fun updateCustomerName(id: String, data: CustomerName) {
        database.updateCustomerName(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.id == id) it.copy(info = it.info.copy(name = data)) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(info = _selectedCustomerData.value.info.copy(name = data))
    }

    fun updateCustomerAddress(id: String, data: Address) {
        database.updateCustomerAddress(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.id == id) it.copy(info = it.info.copy(address = data)) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(info = _selectedCustomerData.value.info.copy(address = data))
    }

    fun updateCustomerContact(id: String, data: ContactInfo) {
        database.updateCustomerContact(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.id == id) it.copy(info = it.info.copy(contactInfo = data)) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(info = _selectedCustomerData.value.info.copy(contactInfo = data))
    }

    fun updateCustomerTickets(id: String, data: List<String>) {
        database.updateCustomerTickets(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.id == id) it.copy(tickets = data) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(tickets = data)
    }

    fun updateCustomerNotes(id: String, data: List<Note>) {
        database.updateCustomerNotes(id, data)

        // Fix List in viewmodel
        _customerDataList.value = _customerDataList.value.map { it ->
            if (it.info.id == id) it.copy(notes = data) else it
        }

        // update selected data as well
        _selectedCustomerData.value = _selectedCustomerData.value.copy(notes = data)
    }

    fun addCustomer(data: CustomerData) {
        _customerDataList.value = _customerDataList.value + data
        database.addCustomer(data)
    }

    fun updateDataFromDb() {
        // Read data from mock db and update it in view model
        changeCustomerDataList(database.getAllCustomerData())
        changeTicketDataList(database.getAllTicketData())
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
        val (customerdata, ticketData) = generateMockData(60)

        database.addAllTicketData(ticketData)

        // Add the generated values to the database
        database.addAllCustomerData(customerdata)
    }

    fun getMockDataFromDb() {
        // Now lets read and set values in the viewmodel
        changeCustomerDataList(database.getAllCustomerData())
    }
}
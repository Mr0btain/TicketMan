package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.data.CustomerData
import org.mr0btain.ticketman.components.data.TicketInfo
import org.mr0btain.ticketman.getPlatform

/**
Main View model for the project.
Used to store data that needs to persist for the lifetime of the app and be used anywhere
 */

class MainViewModel(): ViewModel() {
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
            if (tickets.contains(it.id)) {
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

    // ----------------------------------------------------------------------------------------------------------------------------
}
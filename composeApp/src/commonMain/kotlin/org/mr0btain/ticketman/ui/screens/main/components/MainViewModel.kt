package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.data.ContactInfo
import org.mr0btain.ticketman.components.data.CustomerData
import org.mr0btain.ticketman.components.data.CustomerInfo
import org.mr0btain.ticketman.components.data.generateMockCustomerData
import org.mr0btain.ticketman.getPlatform

/**
Main View model for the project.
Used to store data that needs to persist for the lifetime of the app and be used anywhere
 */

class MainViewModel(): ViewModel() {
    val topAppBarText = mutableStateOf("Main")
    val platform = getPlatform().name

    // Selected main tab index
    private val _selectedMainTabIndex: MutableStateFlow<String> = MutableStateFlow("0")
    var selectedMainTabIndex = _selectedMainTabIndex.asStateFlow()



    // Selected ticket tab index
    private val _selectedTicketTabIndex: MutableStateFlow<String> = MutableStateFlow("0")
    var selectedTicketTabIndex = _selectedTicketTabIndex.asStateFlow()

    // Selected Customer data
    private val _customerDataList: MutableStateFlow<List<CustomerData>> = MutableStateFlow(generateMockCustomerData(40))
    var customerDataList = _customerDataList.asStateFlow()


    // Selected Customer data
    private val _selectedCustomerData: MutableStateFlow<CustomerData> = MutableStateFlow(CustomerData())
    var selectedCustomerData = _selectedCustomerData.asStateFlow()



    // Call to change customer data
    fun changeSelectedCustomerData(customerData: CustomerData) {
        _selectedCustomerData.value = customerData
    }

    // To update the main tab index
    fun changeMainTabIndex(update: String) {
        _selectedMainTabIndex.value = update
    }

    // To update customer data list
    fun changeCustomerDataList(list: List<CustomerData>) {
        _customerDataList.value = list
    }

    // To update the ticket tab index
    fun changeTicketTabIndex(update: String) {
        _selectedTicketTabIndex.value = update
    }
}
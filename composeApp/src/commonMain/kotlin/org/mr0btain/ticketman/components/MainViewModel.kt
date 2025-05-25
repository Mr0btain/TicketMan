package org.mr0btain.ticketman.components

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.data.ContactInfo
import org.mr0btain.ticketman.components.data.CustomerInfo
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








    // To update the main tab index
    fun changeMainTabIndex(update: String) {
        _selectedMainTabIndex.value = update
    }

    // To update the ticket tab index
    fun changeTicketTabIndex(update: String) {
        _selectedTicketTabIndex.value = update
    }

    // For testing
    val mockCustomerInfoList = listOf(
        CustomerInfo("Jane Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("Jack Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("Nathan Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("James Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("George Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("Jane Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("Jane Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("Jane Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
        CustomerInfo("Jane Doe", "123 Wallaby way", ContactInfo("janedoe@gmail.com", listOf("8145678765")), "123"),
    )
}
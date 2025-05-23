package org.mr0btain.ticketman.components

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
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
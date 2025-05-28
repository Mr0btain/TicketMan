package org.mr0btain.ticketman.components.data.components

import org.mr0btain.ticketman.components.data.components.CustomerInfo
import org.mr0btain.ticketman.components.data.components.Note

data class CustomerData(
    val info: CustomerInfo = CustomerInfo(),
    val tickets: List<String> = listOf(),
    val notes: List<Note> = listOf()
)
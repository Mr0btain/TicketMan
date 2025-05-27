package org.mr0btain.ticketman.components.data

data class CustomerData(
    val info: CustomerInfo = CustomerInfo(),
    val tickets: List<String> = listOf(),
    val notes: List<Note> = listOf()
)
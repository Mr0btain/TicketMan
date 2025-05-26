package org.mr0btain.ticketman.components.data

data class CustomerInfo(
    val id: String = "",
    val name: CustomerName = CustomerName(),
    val address: Address = Address(),
    val contactInfo: ContactInfo = ContactInfo(),
    val mostRecentTicket: String = ""
)

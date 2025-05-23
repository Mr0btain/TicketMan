package org.mr0btain.ticketman.components.data

data class CustomerInfo(
    val name: String,
    val address: String,
    val contactInfo: ContactInfo,
    val mostRecentTicket: String
)

package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

data class CustomerData(
    val info: CustomerInfo = CustomerInfo(),
    val tickets: List<String> = listOf(),
    val notes: List<Note> = listOf()
)

/**
Only used for serializing into json for database
 */
@Serializable
data class TicketList(val list: List<String> = listOf())
package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.shared.Note

@Serializable
data class CustomerData(
    override val identifier: String = "",
    val info: CustomerInfo = CustomerInfo(),
    val tickets: List<String> = listOf(),
    val notes: List<Note> = listOf()
): MainDataItem

/**
Only used for serializing into json for database
 */
@Serializable
data class TicketList(val list: List<String> = listOf())
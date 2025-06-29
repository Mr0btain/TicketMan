package org.mr0btain.ticketman.data.customer

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.shared.Note

@Serializable
data class CustomerData(
    override val identifier: Int = Int.MAX_VALUE,
    val info: CustomerInfo = CustomerInfo(),
    val tickets: List<Int> = listOf(),
    val notes: List<Note> = listOf()
): MainDataItem

/**
Only used for serializing into json for database
 */
@Serializable
data class TicketList(val list: List<Int> = listOf())
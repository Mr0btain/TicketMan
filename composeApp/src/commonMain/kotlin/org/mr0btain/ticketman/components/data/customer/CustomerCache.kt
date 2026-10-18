package org.mr0btain.ticketman.components.data.customer

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket

class CustomerCache(maxSize: Int): CustomCache<CustomerCache.CustomerData>(maxSize) {

    fun put(
        customer: Customer,
        notes: List<Note> = emptyList(),
        tickets: List<Ticket> = emptyList()
    ) {
        cache.put(customer.identifier.toString(), CustomerData(customer, notes, tickets))
    }

    data class CustomerData(
        val customer: Customer,
        val notes: List<Note>,
        val tickets: List<Ticket>
    )
}
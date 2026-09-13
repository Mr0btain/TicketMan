package org.mr0btain.ticketman.components.data.customer

import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import kotlin.uuid.Uuid

class CustomerCache(maxSize: Int) {
    private val cache = LruCache<String, CustomersData>(maxSize, "CustomerCache")

    suspend fun put(
        customer: Customer,
        notes: List<Note> = emptyList(),
        tickets: List<Ticket> = emptyList()
    ) {
        cache[customer.identifier.toString()] = CustomersData(customer, notes, tickets)
    }

    suspend fun get(id: Uuid): CustomersData? {
        return cache[id.toString()]
    }

    data class CustomersData(
        val customer: Customer,
        val notes: List<Note>,
        val tickets: List<Ticket>
    )
}
package org.mr0btain.ticketman.components.data.customer

import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.*

class CustomerCache {
    private val cache = LruCache<UUID, CustomersData>(15, "CustomerCache")

    suspend fun get(identifier: UUID): CustomersData? =
        cache.get(identifier)

    suspend fun put(
        customer: Customer,
        notes: List<Note> = emptyList(),
        tickets: List<Ticket> = emptyList()
    ) {
        cache.put(
            customer.identifier,
            CustomersData(customer, notes, tickets)
        )
    }

    data class CustomersData(
        val customer: Customer,
        val notes: List<Note>,
        val tickets: List<Ticket>
    )
}
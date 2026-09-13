package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import kotlin.uuid.Uuid

class CustomerDatabaseImpl: Database<Customer>() {
    override fun add(new: Customer): Boolean {
        TODO("Not yet implemented")
    }

    override fun update(item: Customer): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(id: Uuid): Customer? {
        TODO("Not yet implemented")
    }

    override fun getNotes(id: Uuid): List<Note>? {
        TODO("Not yet implemented")
    }

    fun getTickets(id: Uuid): List<Ticket>? {
        TODO("Not yet implemented")
    }

    override fun delete(item: Uuid): Boolean {
        TODO("Not yet implemented")
    }

    override fun addNotes(
        notes: List<Note>,
        id: Uuid
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun search(
        query: String,
        max: Int
    ): List<Customer> {
        TODO("Not yet implemented")
    }

    fun addTicket(customer: Uuid, ticket: Uuid): Boolean {
        TODO("Not yet implemented")
    }

    fun updateName(id: Uuid, name: String): Boolean {
        TODO("Not yet implemented")
    }

    fun updateEmail(id: Uuid, email: String): Boolean {
        TODO("Not yet implemented")
    }

    fun updatePhone(id: Uuid, phone: String): Boolean {
        TODO("Not yet implemented")
    }
}
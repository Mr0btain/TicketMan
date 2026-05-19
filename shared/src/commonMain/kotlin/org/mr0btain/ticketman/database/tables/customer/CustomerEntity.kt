package org.mr0btain.ticketman.database.tables.customer

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import java.util.*

class CustomerEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<CustomerEntity>(CustomerTable)

    var name by CustomerTable.name
    var phone by CustomerTable.phone
    var email by CustomerTable.email
    var address by CustomerTable.address

    // For notes
    var notes by NoteEntity via CustomerNoteTable

    // For tickets
    var tickets by TicketEntity via CustomerTicketTable

    fun toCustomer(): Customer {
        return Customer(
            identifier = id.value,
            name = name,
            address = address,
            email = email,
            phone = phone
        )
    }

    fun toIdentity(): Identity {
        return Identity(id.value, name)
    }
}
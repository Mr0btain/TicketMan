package org.mr0btain.ticketman.components.database.tables.customer

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.EntityReference
import kotlin.uuid.Uuid

class CustomerEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<CustomerEntity>(CustomerTable)

    var name by CustomerTable.name
    var phone by CustomerTable.phone
    var email by CustomerTable.email
    var address by CustomerTable.address

    var notes by NoteEntity via CustomerNoteTable

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

    fun toIdentity(): EntityReference {
        return EntityReference(id.value, name)
    }
}
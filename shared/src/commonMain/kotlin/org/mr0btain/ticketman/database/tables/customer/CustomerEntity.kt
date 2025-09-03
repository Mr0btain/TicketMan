package org.mr0btain.ticketman.database.tables.customer

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.shared.toStringName
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.logging.AppLogger
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class CustomerEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<CustomerEntity>(CustomerTable)

    var name by CustomerTable.name
    var address by CustomerTable.address
    var contactInfo by CustomerTable.contactInfo
    var time by CustomerTable.time

    // For notes
    var notes by NoteEntity via CustomerNoteTable

    // For tickets
    var tickets by TicketEntity via CustomerTicketTable
}

@OptIn(ExperimentalUuidApi::class)
fun CustomerEntity.toCustomer(): Customer {
    return Customer(
        identifier = id.value.toKotlinUuid(),
        name = name,
        address = address,
        contactInfo = contactInfo,
        time = time
    )
}
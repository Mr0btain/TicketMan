package org.mr0btain.ticketman.database.tables.ticket

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.uuid.v7toMillis
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

class TicketEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<TicketEntity>(TicketTable)

    var device by TicketTable.device
    var status by TicketTable.status
    var priority by TicketTable.priority

    // For notes
    var notes by NoteEntity via TicketNoteTable
}

@OptIn(ExperimentalUuidApi::class)
fun TicketEntity.toTicket(): Ticket {
    return Ticket(
        identifier = id.value,
        device = device,
        status = status,
        priority = priority,
        time = id.value.v7toMillis()
    )
}
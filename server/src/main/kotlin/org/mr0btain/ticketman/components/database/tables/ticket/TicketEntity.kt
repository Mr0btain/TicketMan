package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.*

class TicketEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<TicketEntity>(TicketTable)

    var device by TicketTable.device
    var status by TicketTable.status
    var priority by TicketTable.priority

    // For notes
    var notes by NoteEntity via TicketNoteTable

    fun toTicket(): Ticket {
        return Ticket(
            identifier = id.value,
            device = device,
            status = status,
            priority = priority
        )
    }
}
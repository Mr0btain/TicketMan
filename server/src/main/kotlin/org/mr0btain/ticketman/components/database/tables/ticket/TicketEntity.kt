package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TicketEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<TicketEntity>(TicketTable)

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
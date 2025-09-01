package org.mr0btain.ticketman.database.tables.ticket

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.NoteImageTable
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class TicketEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<TicketEntity>(TicketTable)

    var device by TicketTable.device
    var status by TicketTable.status
    var priority by TicketTable.priority
    var time by TicketTable.time

    // For images assigned to note
    var notes by NoteEntity via TicketNoteTable
}

@OptIn(ExperimentalUuidApi::class)
fun TicketEntity.toTicket(): Ticket {
    return Ticket(
        identifier = id.value.toKotlinUuid(),
        device = device,
        status = status,
        priority = priority,
        time = time
    )
}
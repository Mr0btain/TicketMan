package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.components.database.tables.note.NoteTable

object TicketNoteTable: Table("ticket_updates") {
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE)
    val note = reference("note_id", NoteTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(ticket, note)
}
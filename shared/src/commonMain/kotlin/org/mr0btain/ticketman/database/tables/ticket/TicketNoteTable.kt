package org.mr0btain.ticketman.database.tables.ticket

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.note.NoteTable

object TicketNoteTable: Table("ticket_updates") {
    val ticket = reference("ticket_id", TicketTable)
    val note = reference("note_id", NoteTable)

    override val primaryKey = PrimaryKey(ticket, note)
}
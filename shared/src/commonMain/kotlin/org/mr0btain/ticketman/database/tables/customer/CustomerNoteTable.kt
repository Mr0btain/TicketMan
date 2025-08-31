package org.mr0btain.ticketman.database.tables.customer

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.note.NoteTable

object CustomerNoteTable: Table("customer_note_table") {
    val customer = reference("customer_id", CustomerTable)
    val note = reference("note_id", NoteTable)

    override val primaryKey = PrimaryKey(customer, note)
}
package org.mr0btain.ticketman.database.tables.user

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.note.NoteTable

object UserNoteTable: Table("user_note") {
    val user = reference("user_id", UserTable)
    val note = reference("note_id", NoteTable)

    override val primaryKey = PrimaryKey(user, note)
}
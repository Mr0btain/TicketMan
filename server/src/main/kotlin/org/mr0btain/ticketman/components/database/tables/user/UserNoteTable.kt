package org.mr0btain.ticketman.components.database.tables.user

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.components.database.tables.note.NoteTable
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object UserNoteTable: Table("user_note_table") {
    val user = reference("user_id", UserTable)
    val note = reference("note_id", NoteTable)

    override val primaryKey = PrimaryKey(user, note)
}
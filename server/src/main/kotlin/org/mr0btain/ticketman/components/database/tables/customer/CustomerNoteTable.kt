package org.mr0btain.ticketman.components.database.tables.customer

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.components.database.tables.note.NoteTable
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object CustomerNoteTable : Table("customer_note_table") {
    val customer = reference("customer_id", CustomerTable, onDelete = ReferenceOption.CASCADE)
    val note = reference("note_id", NoteTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(customer, note)
}
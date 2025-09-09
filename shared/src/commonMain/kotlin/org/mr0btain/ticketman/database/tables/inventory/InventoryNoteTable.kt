package org.mr0btain.ticketman.database.tables.inventory

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.note.NoteTable

object InventoryNoteTable: Table("inventory_note_table") {
    val inventory = reference("inventory_id", InventoryTable, onDelete = ReferenceOption.CASCADE)
    val note = reference("note_id", NoteTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(inventory, note)
}
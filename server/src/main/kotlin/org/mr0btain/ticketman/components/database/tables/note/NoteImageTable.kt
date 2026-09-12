package org.mr0btain.ticketman.components.database.tables.note

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.components.database.tables.image.ImageTable

object NoteImageTable : Table("note_image_table") {
    val note = reference("note_id", NoteTable, onDelete = ReferenceOption.CASCADE)
    val image = reference("image_id", ImageTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(note, image)
}
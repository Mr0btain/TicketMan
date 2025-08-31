package org.mr0btain.ticketman.database.tables.note

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.image.ImageTable

object NoteImageTable : Table("note_image_table") {
    val note = reference("note_id", NoteTable)
    val image = reference("image_id", ImageTable)

    override val primaryKey = PrimaryKey(note, image)
}
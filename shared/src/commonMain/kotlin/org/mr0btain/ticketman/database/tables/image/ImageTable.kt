package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.mr0btain.ticketman.database.tables.note.NoteTable

object ImageTable : LongIdTable() {
    val path = varchar("path", 512)
    val time = long("time")
}

/*
object ImageTable: LongIdTable("images") {
    val image = blob("image")
    val time = long("time")
}
 */
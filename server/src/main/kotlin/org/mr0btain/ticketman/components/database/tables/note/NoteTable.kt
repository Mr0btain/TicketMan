package org.mr0btain.ticketman.components.database.tables.note

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable

object NoteTable: UUIDTable("notes") {
    val author = text("author")
    val message = text("message")
}
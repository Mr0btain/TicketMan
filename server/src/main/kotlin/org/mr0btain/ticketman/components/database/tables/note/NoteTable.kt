package org.mr0btain.ticketman.components.database.tables.note

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object NoteTable: UuidTable("notes") {
    val author = text("author")
    val message = text("message")
}
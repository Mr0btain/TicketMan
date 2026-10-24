package org.mr0btain.ticketman.components.database.tables.note

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object NoteTable: UuidTable("notes") {
    val author = text("author")
    val message = text("message")

    // Added with the customer status page
    val customerVisible = bool("customer_visible").default(false)
}
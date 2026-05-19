package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable

object ImageTable : UUIDTable() {
    val directory = varchar("path", 512)
    val description = text("description")
    val tags = array<String>("tags")
}
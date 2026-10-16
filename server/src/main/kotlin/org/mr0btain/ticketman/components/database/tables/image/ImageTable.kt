package org.mr0btain.ticketman.components.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object ImageTable : UuidTable("image_table") {
    val directory = varchar("path", 512)
    val description = text("description")
    val tags = array<String>("tags")
}
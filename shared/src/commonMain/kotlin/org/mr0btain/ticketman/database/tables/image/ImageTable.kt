package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object ImageTable : UUIDTable() {
    val path = varchar("path", 512)
    val time = long("time")
}
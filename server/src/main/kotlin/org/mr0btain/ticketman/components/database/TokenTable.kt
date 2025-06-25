package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object TokenTable: IntIdTable("token") {
    val identifier = text("identifier")
    val token = text("token")
}
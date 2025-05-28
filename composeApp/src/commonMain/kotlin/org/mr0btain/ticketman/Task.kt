package org.mr0btain.ticketman

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

const val MAX_VARCHAR_LENGTH = 128

object Tasks : IntIdTable("tasks") {
    val title = varchar("name", MAX_VARCHAR_LENGTH).index("nameId")
    val description = varchar("description", MAX_VARCHAR_LENGTH)
    val isCompleted = bool("completed").default(false)
}
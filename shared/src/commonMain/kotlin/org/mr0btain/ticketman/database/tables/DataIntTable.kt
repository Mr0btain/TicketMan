package org.mr0btain.ticketman.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.shared.Notes

open class DataIntTable(name: String): IntIdTable(name) {
    val identifier = text("identifier")
    val time = long("time")
}
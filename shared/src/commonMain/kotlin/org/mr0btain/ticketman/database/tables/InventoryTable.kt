package org.mr0btain.ticketman.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.shared.Notes

object InventoryTable : IntIdTable("inventory"), DataTable {
    val identifier = text("number")
    val name = text("name")
    val description = text("description")
    val stock = integer("stock")
    val cost = varchar("cost", 100)
    val price = varchar("price", 100)
    val notes = json<Notes>("notes", Json.Default)
    val time = long("time")
}
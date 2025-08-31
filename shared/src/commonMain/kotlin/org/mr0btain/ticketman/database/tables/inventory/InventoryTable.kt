package org.mr0btain.ticketman.database.tables.inventory

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.shared.Note

object InventoryTable : UUIDTable("inventory") {
    val name = text("name")
    val description = text("description")
    val stock = integer("stock")
    val cost = varchar("cost", 100)
    val price = varchar("price", 100)
    val notes = json<List<Note>>("notes", Json { ignoreUnknownKeys = true })
    val time = long("time")
}
package org.mr0btain.ticketman.components.data.database.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object InventoryTable : IntIdTable("inventory") {
    val identifier = integer("number")
    val name = text("name")
    val description = text("description")
    val stock = integer("stock")
    val cost = varchar("cost", 100)
    val price = varchar("price", 100)
    val time = long("time")
}
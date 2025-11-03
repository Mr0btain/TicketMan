package org.mr0btain.ticketman.database.tables.inventory

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object InventoryTable : UUIDTable("inventory") {
    val name = text("name")
    val description = text("description")
    val stock = integer("stock")
    val cost = varchar("cost", 100)
    val price = varchar("price", 100)
}
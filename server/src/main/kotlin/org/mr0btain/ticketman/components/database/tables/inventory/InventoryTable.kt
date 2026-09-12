package org.mr0btain.ticketman.components.database.tables.inventory

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object InventoryTable : UuidTable("inventory") {
    val name = text("name")
    val description = text("description")
    val stock = integer("stock")
    val cost = varchar("cost", 100)
    val price = varchar("price", 100)
}
package org.mr0btain.ticketman.components.database.tables.inventory

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object InventoryTable : UuidTable("inventory") {
    val name = text("name")
    val description = text("description")
    val stock = integer("stock")
    // Prices as typed text from before money was stored in cents, moved into the cents columns on start
    val legacyCost = varchar("cost", 100).default("")
    val legacyPrice = varchar("price", 100).default("")

    val costCents = long("cost_cents").default(0)
    val priceCents = long("price_cents").default(0)
}
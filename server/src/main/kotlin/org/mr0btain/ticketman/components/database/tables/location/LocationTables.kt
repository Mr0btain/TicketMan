package org.mr0btain.ticketman.components.database.tables.location

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable

/** The shop's locations. One is [main], made on start, which records from before locations belong to */
object LocationTable : UuidTable("locations") {
    val code = text("code").default("")
    val name = text("name")
    val street = text("street").default("")
    val city = text("city").default("")
    val state = text("state").default("")
    val zip = text("zip").default("")
    val phone = text("phone").default("")
    val email = text("email").default("")
    val taxRate = text("tax_rate").nullable()
    val taxClassRates = json<Map<String, String>>("tax_class_rates", Json { ignoreUnknownKeys = true }).default(emptyMap())
    val main = bool("is_main").default(false)
    val retired = bool("retired").default(false)
    val createdAt = long("created_at").default(0)
}

/**
 * How many of each tracked item are at each location. An item whose `InventoryTable.stock` is `Int.MAX_VALUE`
 * isn't tracked and has no rows; a tracked one with no row at a location has none there
 */
object InventoryStockTable : Table("inventory_stock") {
    val item = reference("item_id", InventoryTable, onDelete = ReferenceOption.CASCADE)
    val location = uuid("location_id")
    val stock = integer("stock").default(0)
    val reorderPoint = integer("reorder_point").nullable()

    override val primaryKey = PrimaryKey(item, location)
}

/** Parts moved between locations, kept for the item's history */
object StockTransferTable : UuidTable("stock_transfers") {
    val item = reference("item_id", InventoryTable, onDelete = ReferenceOption.CASCADE)
    val fromLocation = uuid("from_location")
    val toLocation = uuid("to_location")
    val quantity = integer("quantity")
    val note = text("note").default("")
    val movedBy = text("moved_by").default("")
    val movedAt = long("moved_at").index()
}

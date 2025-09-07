package org.mr0btain.ticketman.database.tables.inventory

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.image.ImageTable

object InventoryImageTable: Table("inventory_image_table") {
    val inventory = reference("inventory_id", InventoryTable)
    val image = reference("image_id", ImageTable)

    override val primaryKey = PrimaryKey(inventory, image)
}
package org.mr0btain.ticketman.components.database.tables.inventory

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.components.database.tables.image.ImageTable

object InventoryImageTable: Table("inventory_image_table") {
    val inventory = reference("inventory_id", InventoryTable, onDelete = ReferenceOption.CASCADE)
    val image = reference("image_id", ImageTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(inventory, image)
}
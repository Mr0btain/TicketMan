package org.mr0btain.ticketman.components.database.tables.inventory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mr0btain.ticketman.components.database.tables.image.ImageEntity
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.inventory.Inventory
import java.util.*

class InventoryEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<InventoryEntity>(InventoryTable)

    var name by InventoryTable.name
    var description by InventoryTable.description
    var stock by InventoryTable.stock
    var cost by InventoryTable.cost
    var price by InventoryTable.price

    // For notes
    var notes by NoteEntity via InventoryNoteTable

    // For images
    var images by ImageEntity via InventoryImageTable

    fun toInventory(): Inventory {
        return Inventory(
            identifier = id.value,
            name = name,
            description = description,
            stock = stock,
            cost = cost,
            price = price
        )
    }
}
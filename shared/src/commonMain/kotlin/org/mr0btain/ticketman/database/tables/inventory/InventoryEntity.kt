package org.mr0btain.ticketman.database.tables.inventory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.uuid.v7toMillis
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

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
}

@OptIn(ExperimentalUuidApi::class)
fun InventoryEntity.toInventoryItem(): Inventory {
    return Inventory(
        identifier = id.value,
        name = name,
        description = description,
        stock = stock,
        cost = cost,
        price = price,
        time = id.value.v7toMillis()
    )
}
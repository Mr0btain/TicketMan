package org.mr0btain.ticketman.components.database.tables.inventory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.mr0btain.ticketman.components.database.tables.image.ImageEntity
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.inventory.Inventory
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class InventoryEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<InventoryEntity>(InventoryTable)

    var name by InventoryTable.name
    var description by InventoryTable.description
    var stock by InventoryTable.stock
    var legacyCost by InventoryTable.legacyCost
    var legacyPrice by InventoryTable.legacyPrice
    var costCents by InventoryTable.costCents
    var priceCents by InventoryTable.priceCents
    var taxClass by InventoryTable.taxClass
    var sku by InventoryTable.sku
    var barcode by InventoryTable.barcode
    var category by InventoryTable.category
    var reorderPoint by InventoryTable.reorderPoint

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
            costCents = costCents,
            priceCents = priceCents,
            taxClass = taxClass,
            sku = sku,
            barcode = barcode,
            category = category,
            reorderPoint = reorderPoint
        )
    }
}
package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.Uuid

class InventoryDatabaseImpl: Database<Inventory>() {
    override fun add(new: Inventory): Boolean {
        TODO("Not yet implemented")
    }

    override fun update(item: Inventory): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(id: Uuid): Inventory? {
        TODO("Not yet implemented")
    }

    override fun getNotes(id: Uuid): List<Note>? {
        TODO("Not yet implemented")
    }

    override fun getPagedItems(
        offset: Int,
        limit: Int
    ): List<Inventory> {
        TODO("Not yet implemented")
    }

    override fun delete(item: Uuid): Boolean {
        TODO("Not yet implemented")
    }

    override fun addNotes(
        notes: List<Note>,
        id: Uuid
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun search(
        query: String,
        max: Int
    ): List<Inventory> {
        TODO("Not yet implemented")
    }

    fun addImages(id: Uuid, images: List<StoredImage>): Boolean {
        TODO("Not yet implemented")
    }

    fun updateName(id: Uuid, name: String): Boolean {
        TODO("Not yet implemented")
    }

    fun updatePrice(id: Uuid, price: Double): Boolean {
        TODO("Not yet implemented")
    }

    fun updateStock(id: Uuid, stock: Int): Boolean {
        TODO("Not yet implemented")
    }

    fun updateCost(id: Uuid, cost: Double): Boolean {
        TODO("Not yet implemented")
    }

    fun updateDescription(id: Uuid, description: String): Boolean {
        TODO("Not yet implemented")
    }
}
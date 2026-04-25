package org.mr0btain.ticketman.components.data.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

class InventorySeed: DataSeed() {
    var name: String by mutableStateOf("")
        private set
    var description: String by mutableStateOf("")
        private set
    var stock: Int by mutableStateOf(0)
        private set
    var cost: Double by mutableStateOf(0.0)
        private set
    var price: Double by mutableStateOf(0.0)
        private set

    val inventory: Inventory get() = Inventory(
        identifier = identifier,
        name = name,
        description = description,
        stock = stock,
        cost = cost.toString(),
        price = price.toString()
    )

    // --- Initial setting up with data ---
    fun setInitial(
        data: Inventory,
        notes: List<Note> = emptyList()
    ) {
        name = data.name
        description = data.description
        stock = data.stock
        cost = data.cost.toDoubleOrNull() ?: 0.0
        price = data.price.toDoubleOrNull() ?: 0.0
        time = UUIDv7.getLongTime(data.identifier)

        setNotes(notes)

        markClean()
        isNew = false
    }

    // -- Setters that automatically mark changes --
    fun changeName(newName: String) {
        if (name != newName) {
            name = newName
            _changedFields.add(ChangedField.NAME)
        }
    }

    fun changeDescription(newDescr: String) {
        if (description != newDescr) {
            description = newDescr
            _changedFields.add(ChangedField.DESCRIPTION)
        }
    }

    fun changeStock(newStock: Int) {
        if (stock != newStock) {
            stock = newStock
            _changedFields.add(ChangedField.STOCK)
        }
    }

    fun changePrice(newPrice: Double) {
        if (price != newPrice) {
            price = newPrice
            _changedFields.add(ChangedField.PRICE)
        }
    }

    // Reset all fields
    override fun clear() {
        identifier = UUIDv7.randomUUID()
        name = ""
        description = ""
        stock = 0
        cost = 0.0
        price = 0.0
        _notes.clear()
        _images.clear()
        time = UUIDv7.getLongTime(identifier)
        isNew = true

        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(identifier, name, description, stock, cost, price, _notes, _images, time).hashCode()
        _changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String =
        "InventorySeed(name=$name,description=$description,stock=$stock,cost=$cost,price=$price, notes=$_notes)"

    fun changeCost(newCost: Double) {
        if (newCost != cost) {
            cost = newCost
            _changedFields.add(ChangedField.COST)
        }
    }

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val name: String = "",
        val description: String = "",
        val stock: Int = 0,
        val cost: Double = 0.0,
        val price: Double = 0.0,
        val notes: List<Note> = emptyList(),
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}
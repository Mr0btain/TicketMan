package org.mr0btain.ticketman.components.data.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class InventorySeed: DataSeed("InventorySeed") {
    var name: String by mutableStateOf("")
        private set
    var description: String by mutableStateOf("")
        private set
    var stock: Int by mutableStateOf(0)
        private set
    // In dollars for editing, saved as cents
    var cost: Double by mutableStateOf(0.0)
        private set
    var price: Double by mutableStateOf(0.0)
        private set

    val inventory: Inventory get() = Inventory(
        identifier = identifier,
        name = name,
        description = description,
        stock = stock,
        costCents = Money.fromDollars(cost),
        priceCents = Money.fromDollars(price)
    )

    // --- Initial setting up with data ---
    fun setInitial(
        data: Inventory,
        notes: List<Note> = emptyList()
    ) {
        identifier = data.identifier
        name = data.name
        description = data.description
        stock = data.stock
        cost = Money.toDollars(data.costCents)
        price = Money.toDollars(data.priceCents)

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

    fun changeCost(newCost: Double) {
        if (newCost != cost) {
            cost = newCost
            _changedFields.add(ChangedField.COST)
        }
    }

    // Reset all fields
    override fun clear() {
        identifier = Uuid.generateV7()
        name = ""
        description = ""
        stock = 0
        cost = 0.0
        price = 0.0
        _notes.clear()
        _images.clear()
        isNew = true

        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(identifier, name, description, stock, cost, price, _notes).hashCode()
        _changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String =
        "InventorySeed(name=$name,description=$description,stock=$stock,cost=$cost,price=$price, notes=$_notes)"

    private data class State(
        val identifier: Uuid = Uuid.generateV7(),
        val name: String = "",
        val description: String = "",
        val stock: Int = 0,
        val cost: Double = 0.0,
        val price: Double = 0.0,
        val notes: List<Note> = emptyList(),
        val images: List<ImageBitmap> = emptyList()
    )
}
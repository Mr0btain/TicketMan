package org.mr0btain.ticketman.components.data.inventory

import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID

class InventorySeed: DataSeed() {
    private var _name: String = ""
    private var _description: String = ""
    private var _stock: Int = 0
    private var _cost: Double = 0.00
    private var _price: Double = 0.00

    val inventory: Inventory get() = Inventory(
        identifier = identifier,
        name = _name,
        description = _description,
        stock = _stock,
        cost = _cost.toString(),
        price = _price.toString()
    )

    val name: String get() = _name
    val description: String get() = _description
    val stock: Int get() = _stock
    val cost: Double get() = _cost
    val price: Double get() = _price

    // snapshot of the last saved state
    private var savedState: State = State()

    // --- Initial setting up with data ---
    fun setInitial(
        data: Inventory,
        notes: Map<Note, List<StoredImage>>
    ) {
        _name = data.name
        _description = data.description
        _stock = data.stock
        _cost = data.cost.toDoubleOrNull() ?: 0.0
        _price = data.price.toDoubleOrNull() ?: 0.0
        _notes = notes.toMutableMap()
        _time = UUIDv7.getLongTime(data.identifier)

        markClean()
        _isNew = false
    }

    // -- Setters that automatically mark changes --
    fun setName(newName: String) {
        if (_name != newName) {
            _name = newName
            changedFields.add(ChangedField.NAME)
        }
    }

    fun setDescription(newDescr: String) {
        if (_description != newDescr) {
            _description = newDescr
            changedFields.add(ChangedField.DESCRIPTION)
        }
    }

    fun setStock(newStock: Int) {
        if (_stock != newStock) {
            _stock = newStock
            changedFields.add(ChangedField.STOCK)
        }
    }

    fun setPrice(newPrice: Double) {
        if (_price != newPrice) {
            _price = newPrice
            changedFields.add(ChangedField.PRICE)
        }
    }

    // Reset all fields
    override fun clear() {
        _identifier = UUIDv7.randomUUID()
        _name = ""
        _description = ""
        _stock = 0
        _cost = 0.0
        _price = 0.0
        _notes.clear()
        _images.clear()
        _time = UUIDv7.getLongTime(_identifier)
        _isNew = true

        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(_identifier, _name, _description, _stock, _cost, _price, _notes, _images, _time)
        changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String =
        "InventorySeed(name=$_name,description=$_description,stock=$_stock,cost=$_cost,price=$_price, notes=$_notes)"

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val name: String = "",
        val description: String = "",
        val stock: Int = 0,
        val cost: Double = 0.0,
        val price: Double = 0.0,
        val notes: Map<Note, List<StoredImage>> = emptyMap(),
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}
package org.mr0btain.ticketman.components.data.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
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
    /** The count when the item was loaded, the server only takes a new count if it's still this */
    var loadedStock: Int = 0
        private set
    /** Whether pickups count this item down, `Int.MAX_VALUE` stock means they don't */
    val tracksStock: Boolean get() = stock != Int.MAX_VALUE
    // In dollars for editing, saved as cents
    var cost: Double by mutableStateOf(0.0)
        private set
    var price: Double by mutableStateOf(0.0)
        private set
    /** The shop's tax class it's sold at, null for the main rate */
    var taxClass: String? by mutableStateOf(null)
        private set

    val inventory: Inventory get() = Inventory(
        identifier = identifier,
        name = name,
        description = description,
        stock = stock,
        costCents = Money.fromDollars(cost),
        priceCents = Money.fromDollars(price),
        taxClass = taxClass
    )

    // --- Initial setting up with data ---
    fun setInitial(
        data: Inventory,
        notes: List<Note> = emptyList(),
        images: List<StoredImage> = emptyList()
    ) {
        identifier = data.identifier
        name = data.name
        description = data.description
        stock = data.stock
        loadedStock = data.stock
        cost = Money.toDollars(data.costCents)
        price = Money.toDollars(data.priceCents)
        taxClass = data.taxClass

        setNotes(notes)
        setImages(images)

        markClean()
        isNew = false
    }

    // -- Setters that automatically mark changes --
    fun changeName(newName: String) {
        name = newName
    }

    fun changeDescription(newDescr: String) {
        description = newDescr
    }

    fun changeStock(newStock: Int) {
        stock = newStock
    }

    /** Starts counting from the loaded count, or zero if it wasn't counted before. Stopping forgets the count */
    fun changeTracksStock(track: Boolean) {
        if (track == tracksStock) return
        stock = if (!track) Int.MAX_VALUE else if (loadedStock != Int.MAX_VALUE) loadedStock else 0
    }

    fun changePrice(newPrice: Double) {
        price = newPrice
    }

    fun changeCost(newCost: Double) {
        cost = newCost
    }

    fun changeTaxClass(newClass: String?) {
        taxClass = newClass
    }

    override fun fields() = mapOf(
        ChangedField.NAME to name,
        ChangedField.DESCRIPTION to description,
        ChangedField.STOCK to stock,
        ChangedField.PRICE to price,
        ChangedField.COST to cost,
        ChangedField.TAX_CLASS to taxClass
    )

    // Reset all fields
    override fun clear() {
        identifier = Uuid.generateV7()
        name = ""
        description = ""
        stock = 0
        loadedStock = 0
        cost = 0.0
        price = 0.0
        taxClass = null
        _notes.clear()
        _images.clear()
        isNew = true

        markClean()
    }

    override fun toString(): String =
        "InventorySeed(name=$name,description=$description,stock=$stock,cost=$cost,price=$price, notes=$_notes)"
}
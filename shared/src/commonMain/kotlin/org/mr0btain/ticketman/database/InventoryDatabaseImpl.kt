package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.error.InventoryError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import java.util.*


/**
 * Used for all Inventory data actions in database
 */
@Suppress("PrivatePropertyName")
class InventoryDatabaseImpl(tag: String = "InventoryLocalDatabase") : Database<Inventory>(tag) {
    private val GET_IMAGES_TAG = "$tag:GetImages"
    private val ADD_IMAGES_TAG = "$tag:AddImages"
    private val REMOVE_IMAGES_TAG = "$tag:RemoveImages"
    private val UPDATE_NAME_TAG = "$tag:RemoveImages"
    private val UPDATE_DESCRIPTION_TAG = "$tag:RemoveImages"
    private val UPDATE_STOCK_TAG = "$tag:RemoveImages"
    private val UPDATE_COST_TAG = "$tag:RemoveImages"
    private val UPDATE_PRICE_TAG = "$tag:RemoveImages"

    override fun all(): List<Inventory>? {
        var result: List<Inventory>? = null

        appLogTryCatch(GET_ALL_TAG) {
            // Go into database and convert data to customer data item
            transaction {
                result = InventoryEntity.all().map { item -> item.toInventory() }
            }

            result != null
        }

        return result
    }

    override fun getById(identifier: UUID): Inventory? {
        var result: Inventory? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    result = inventoryEntity.toInventory()
                } ?: AppLogger.i(GET_BY_ID_TAG, InventoryError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<Inventory>? {
        var result: List<Inventory>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = InventoryEntity
                    .all()
                    .orderBy(InventoryTable.name to SortOrder.ASC, InventoryTable.id to SortOrder.DESC)
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toInventory() }
            }

            result != null
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = InventoryEntity.count()
            }

            true
        }
        return count
    }

    override fun addAll(data: List<Inventory>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    InventoryEntity.new(item.identifier) {
                        name = item.name
                        description = item.description
                        stock = item.stock
                        cost = item.cost
                        price = item.price
                    }
                }
            }

            true
        }
    }

    override fun remove(identifier: UUID) {
        appLogTryCatch(REMOVE_TAG) {
            var result = false

            transaction {
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    inventoryEntity.delete()

                    result = true
                } ?: AppLogger.i(REMOVE_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    override fun search(query: String, limit: Int): List<SearchResult>? {
        return appLogTryCatch<List<SearchResult>?>(SEARCH_TAG) {
            transaction {
                val pattern = "${query.lowercase()}%"

                Pair(
                    InventoryEntity
                        .find {
                            (InventoryTable.name.lowerCase() like pattern) or
                                    (InventoryTable.id.castTo(VarCharColumnType()) like pattern)
                        }
                        .orderBy(InventoryTable.name to SortOrder.ASC, InventoryTable.id to SortOrder.ASC)
                        .limit(limit)
                        .map { inventoryEntity ->
                            SearchResult(
                                identifier = inventoryEntity.id.value,
                                text = when {
                                    inventoryEntity.name.startsWith(query, true) -> inventoryEntity.name
                                    inventoryEntity.id.value.toString().startsWith(query, true) -> inventoryEntity.id.value.toString()
                                    else -> inventoryEntity.name
                                }
                            )
                        },
                    true
                )
            }
        }
    }

    override fun add(data: Inventory) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                InventoryEntity.new(data.identifier) {
                    name = data.name
                    description = data.description
                    stock = data.stock
                    cost = data.cost
                    price = data.price
                }

                true
            }
        }
    }

    override fun update(data: Inventory) {
        appLogTryCatch(UPDATE_TAG) {
            var result = false

            transaction {
                InventoryEntity
                    .findById(data.identifier)
                    ?.apply {
                        name = data.name
                        description = data.description
                        stock = data.stock
                        cost = data.cost
                        price = data.price

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    fun updateName(identifier: UUID, newName: String) {
        appLogTryCatch(UPDATE_NAME_TAG) {
            var result = false

            transaction {
                InventoryEntity
                    .findById(identifier)
                    ?.apply {
                        name = newName

                        result = true
                    } ?: AppLogger.i(UPDATE_NAME_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    fun updateDescription(identifier: UUID, newDescription: String) {
        appLogTryCatch(UPDATE_DESCRIPTION_TAG) {
            var result = false

            transaction {
                InventoryEntity
                    .findById(identifier)
                    ?.apply {
                        description = newDescription

                        result = true
                    } ?: AppLogger.i(UPDATE_DESCRIPTION_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    fun updateStock(identifier: UUID, newStock: Int) {
        appLogTryCatch(UPDATE_STOCK_TAG) {
            var result = false

            transaction {
                InventoryEntity
                    .findById(identifier)
                    ?.apply {
                        stock = newStock

                        result = true
                    } ?: AppLogger.i(UPDATE_STOCK_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    fun updateCost(identifier: UUID, newCost: String) {
        appLogTryCatch(UPDATE_COST_TAG) {
            var result = false

            transaction {
                InventoryEntity
                    .findById(identifier)
                    ?.apply {
                        cost = newCost

                        result = true
                    } ?: AppLogger.i(UPDATE_COST_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    fun updatePrice(identifier: UUID, newPrice: String) {
        appLogTryCatch(UPDATE_PRICE_TAG) {
            var result = false

            transaction {
                InventoryEntity
                    .findById(identifier)
                    ?.apply {
                        price = newPrice

                        result = true
                    } ?: AppLogger.i(UPDATE_PRICE_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    override fun addNote(identifier: UUID, note: Note) {
        appLogTryCatch(ADD_NOTE_TAG) {
            var result = false

            transaction {
                // Insert the note into database and get its entity
                insertNote(note)?.let { noteEntity ->
                    // Add note to notes
                    InventoryEntity.findByIdAndUpdate(identifier) { inventoryEntity ->
                        inventoryEntity.notes = SizedCollection(inventoryEntity.notes + noteEntity)

                        result = true
                    } ?: AppLogger.i(ADD_NOTE_TAG, InventoryError.NOT_FOUND)
                }
            }

            result
        }
    }

    override fun getNotes(identifier: UUID): List<Note>? {
        var result: List<Note>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    result = inventoryEntity.notes.map { noteEntity ->
                        noteEntity.toNote()
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, InventoryError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    /**
     * Gets images for an inventory item
     * @param identifier The identifier of the parent inventory item
     */
    fun getImages(identifier: UUID): List<StoredImage>? {
        // Result list
        var result: List<StoredImage>? = null

        appLogTryCatch(GET_IMAGES_TAG) {
            transaction {
                // Find inventory item
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    // Add found images to result, converting them to stored image
                    result = inventoryEntity.images.map { it.toStoredImage() }
                } ?: AppLogger.i(GET_IMAGES_TAG, InventoryError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    /**
     * Adds an image to the ImageTable and links it to an inventory item
     * @param identifier The identifier of the inventory item
     * @param images List of images to add
     */
    fun addImages(identifier: UUID, images: List<StoredImage>) {
        appLogTryCatch(ADD_IMAGES_TAG) {
            var result = false

            transaction {
                // Find inventory item
                InventoryEntity.findByIdAndUpdate(identifier) { inventoryEntity ->
                    // Add images to table
                    val imageEntities = images.map { storedImage ->
                        ImageEntity.new(storedImage.identifier) {
                            directory = storedImage.directory
                        }
                    }

                    // Add them to inventory
                    inventoryEntity.images = SizedCollection(inventoryEntity.images + imageEntities)

                    result = true
                } ?: AppLogger.m(ADD_IMAGES_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }

    /** Removes an image linked to an inventory item. This will delete it from our ImageTable but WILL NOT delete the image file itself.
     * @param identifier The identifier of inventory item
     * @param images List of images to remove
     */
    fun removeImages(identifier: UUID, images: List<StoredImage>) {
        appLogTryCatch(REMOVE_IMAGES_TAG) {
            var result = false
            val imageIds = images.map { it.identifier }

            transaction {
                // Find inventory item
                InventoryEntity.findByIdAndUpdate(identifier) { inventoryEntity ->
                    inventoryEntity.images.forEach { imageEntity ->
                        if (imageIds.contains(imageEntity.id.value)) imageEntity.delete()
                    }

                    result = true
                } ?: AppLogger.m(REMOVE_IMAGES_TAG, InventoryError.NOT_FOUND)
            }

            result
        }
    }
}
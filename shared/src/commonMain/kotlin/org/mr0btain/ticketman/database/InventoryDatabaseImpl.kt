package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.image.toStoredImage
import org.mr0btain.ticketman.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.inventory.toInventoryItem
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.error.InventoryError
import org.mr0btain.ticketman.error.SimpleResult
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

    override fun all(): List<Inventory>? {
        var result: List<Inventory>? = null

        appLogTryCatch(GET_ALL_TAG) {
            // Go into database and convert data to customer data item
            transaction {
                result = InventoryEntity.all().map { item -> item.toInventoryItem() }
            }

            if (result != null)
                AppLogger.m(GET_ALL_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_ALL_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun getById(identifier: UUID): Inventory? {
        var result: Inventory? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    result = inventoryEntity.toInventoryItem()
                    AppLogger.m(GET_BY_ID_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(GET_BY_ID_TAG, InventoryError.NOT_FOUND)
            }
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
                    .map { it.toInventoryItem() }
            }

            if (result != null)
                AppLogger.m(GET_PAGED_ITEMS_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_PAGED_ITEMS_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = InventoryEntity.count()
            }
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

            AppLogger.m(ADD_ALL_TAG, SimpleResult.SUCCESS)
        }
    }

    override fun remove(identifier: UUID) {
        appLogTryCatch(REMOVE_TAG) {
            transaction {
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    inventoryEntity.delete()

                    AppLogger.m(REMOVE_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(REMOVE_TAG, InventoryError.NOT_FOUND)
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

                AppLogger.m(ADD_TAG, SimpleResult.SUCCESS)
            }
        }
    }

    override fun update(data: Inventory) {
        appLogTryCatch(UPDATE_TAG) {
            transaction {
                InventoryEntity
                    .findById(data.identifier)
                    ?.apply {
                        name = data.name
                        description = data.description
                        stock = data.stock
                        cost = data.cost
                        price = data.price

                        AppLogger.m(UPDATE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(UPDATE_TAG, InventoryError.NOT_FOUND)
            }
        }
    }

    override fun addNote(identifier: UUID, note: Note, storedImages: List<StoredImage>) {
        appLogTryCatch(ADD_NOTE_TAG) {
            transaction {
                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to notes
                    InventoryEntity.findByIdAndUpdate(identifier) { inventoryEntity ->
                        inventoryEntity.notes = SizedCollection(inventoryEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, InventoryError.NOT_FOUND)
                }
            }
        }
    }

    override fun getNotes(identifier: UUID): Map<Note, List<StoredImage>>? {
        var result: Map<Note, List<StoredImage>>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                InventoryEntity.findById(identifier)?.let { inventoryEntity ->
                    result = inventoryEntity.notes.associate { noteEntity ->
                        noteEntity.toNote() to noteEntity.images.map { it.toStoredImage() }
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, InventoryError.NOT_FOUND)
            }

            if (result != null)
                AppLogger.m(GET_NOTES_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_NOTES_TAG, SimpleResult.FAIL)
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

            if (result != null)
                AppLogger.m(GET_IMAGES_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_IMAGES_TAG, SimpleResult.FAIL)
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
            transaction {
                // Find inventory item
                InventoryEntity.findByIdAndUpdate(identifier) { inventoryEntity ->
                    // Add images to table
                    val imageEntities = images.map { storedImage ->
                        ImageEntity.new(storedImage.identifier) {
                            path = storedImage.path
                        }
                    }

                    // Add them to inventory
                    inventoryEntity.images = SizedCollection(inventoryEntity.images + imageEntities)

                    AppLogger.m(ADD_IMAGES_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.m(ADD_IMAGES_TAG, InventoryError.NOT_FOUND)
            }
        }
    }

    /** Removes an image linked to an inventory item. This will delete it from our ImageTable but WILL NOT delete the image file itself.
     * @param identifier The identifier of inventory item
     * @param images List of images to remove
     */
    fun removeImages(identifier: UUID, images: List<StoredImage>) {
        appLogTryCatch(REMOVE_IMAGES_TAG) {
            val imageIds = images.map { it.identifier }
            transaction {
                // Find inventory item
                InventoryEntity.findByIdAndUpdate(identifier) { inventoryEntity ->
                    inventoryEntity.images.forEach { imageEntity ->
                        if (imageIds.contains(imageEntity.id.value)) imageEntity.delete()
                    }

                    AppLogger.m(REMOVE_IMAGES_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.m(REMOVE_IMAGES_TAG, InventoryError.NOT_FOUND)
            }
        }
    }
}
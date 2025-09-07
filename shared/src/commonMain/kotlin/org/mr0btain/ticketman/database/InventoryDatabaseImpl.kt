package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import org.mr0btain.ticketman.database.tables.image.toStoredImage
import org.mr0btain.ticketman.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.database.tables.inventory.InventoryNoteTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.inventory.toInventoryItem
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class InventoryDatabaseImpl(
    private val tag : String = "InventoryLocalDatabase",
    private val shouldLog: Boolean = false
) : Database<Inventory>(
    tag = tag,
    shouldLog = shouldLog
) {
    private val GET_IMAGES_TAG = "$tag:GetImages"
    private val ADD_IMAGES_TAG = "$tag:AddImages"
    private val REMOVE_IMAGES_TAG = "$tag:RemoveImages"

    /** Gets all items from database */
    override fun all(): List<Inventory>? {
        var result = listOf<Inventory>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = InventoryEntity.all().map { item -> item.toInventoryItem() }

                AppLogger.m(GET_ALL_TAG, SimpleResult.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleResult.FAIL)
            return null
        }

        return result
    }

    /** Gets an item by its identifier */
    override fun getById(identifier: Uuid): Inventory? {
        var result: Inventory? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = InventoryEntity.findById(identifier.toJavaUuid())?.toInventoryItem()

                AppLogger.m(GET_BY_ID_TAG, SimpleResult.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(GET_BY_ID_TAG, SimpleResult.FAIL)
            return null
        }

        return result
    }

    /**
     * Gets a list of items limited by the page size at the designated offset
     * @param offset The starting offset to get items
     * @param pageSize The amount of items to fetch
     */
    override fun getPagedItems(offset: Long, pageSize: Int): List<Inventory> {
        val result = mutableListOf<Inventory>()

        try {
            transaction {
                result.addAll(
                    InventoryEntity
                        .all()
                        .orderBy(InventoryTable.name to SortOrder.ASC, InventoryTable.time to SortOrder.DESC)
                        .offset(offset)
                        .limit(pageSize)
                        .map { it.toInventoryItem() }
                )
            }

            AppLogger.m(GET_PAGED_ITEMS_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.i(GET_PAGED_ITEMS_TAG, SimpleResult.FAIL)
        }

        return result
    }

    /** Gets the total count of items in the database */
    override fun count(): Long = InventoryEntity.count()

    /** Adds all items to the database */
    override fun addAll(data: List<Inventory>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                data.forEach { item ->
                    // Convert kotlin uuid to java for database
                    val identifier = item.identifier.toJavaUuid()

                    // Insert into database
                    InventoryEntity.new(identifier) {
                        name = item.name
                        description = item.description
                        stock = item.stock
                        cost = item.cost
                        price = item.price
                        time = item.time
                    }
                }

                AppLogger.m(ADD_ALL_TAG, SimpleResult.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleResult.FAIL)
            return false
        }
    }

    /** Removes an item from the database */
    override fun remove(identifier: Uuid): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                InventoryEntity.findById(identifier.toJavaUuid())?.delete()

                AppLogger.m(REMOVE_TAG, SimpleResult.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleResult.FAIL)
            return false
        }
    }

    /** Adds an item to the database */
    override fun add(data: Inventory): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                val identifier = data.identifier.toJavaUuid()

                // Insert into database
                InventoryEntity.new(identifier) {
                    name = data.name
                    description = data.description
                    stock = data.stock
                    cost = data.cost
                    price = data.price
                    time = data.time
                }

                AppLogger.m(ADD_TAG, SimpleResult.SUCCESS)
            }

            return true
        } catch (ex: Exception) {
            AppLogger.i(ADD_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
            return false
        }
    }

    /** Updates an item in the database */
    override fun update(data: Inventory): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                InventoryEntity
                    .findById(data.identifier.toJavaUuid())
                    ?.apply {
                        name = data.name
                        description = data.description
                        stock = data.stock
                        cost = data.cost
                        price = data.price
                        time = data.time
                    }

                AppLogger.m(UPDATE_TAG, SimpleResult.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(UPDATE_TAG, SimpleResult.FAIL)
            return false
        }
    }

    override fun addNote(identifier: Uuid, note: Note, storedImages: List<StoredImage>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to notes
                    InventoryEntity.findByIdAndUpdate(identifier.toJavaUuid()) { inventoryEntity ->
                        inventoryEntity.notes = SizedCollection(inventoryEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, "Inventory not found! $identifier")
                } ?: AppLogger.i(ADD_NOTE_TAG, "Note entity was null!")
            }

            return true
        } catch (ex: Exception) {
            AppLogger.i(ADD_NOTE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }

        return false
    }

    override fun getNotes(identifier: Uuid): Map<Note, List<StoredImage>> {
        val result = mutableMapOf<Note, List<StoredImage>>()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                InventoryEntity.findById(identifier.toJavaUuid())?.let { inventoryEntity ->
                    inventoryEntity.notes.forEach { noteEntity ->
                        result[noteEntity.toNote()] = noteEntity.images.map { it.toStoredImage() }
                    }

                    AppLogger.m(GET_NOTES_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(GET_NOTES_TAG, "Could not find inventory!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_NOTES_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }

        return result
    }

    override fun removeNote(identifier: Uuid): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Find the note if it exists
                val note = NoteEntity.findById(identifier.toJavaUuid()) ?: return@transaction

                // No clue if this actually works or not??
                InventoryTable
                    .join(
                        InventoryNoteTable, onColumn = InventoryTable.id, otherColumn = InventoryNoteTable.inventory,
                        joinType = JoinType.INNER,
                    )
                    .select(InventoryNoteTable.note eq note.id)
                    .limit(1)
                    .map { InventoryEntity.wrapRow(it) }
                    .firstOrNull()
                    ?.let { inventoryEntity ->
                        inventoryEntity.notes = SizedCollection(inventoryEntity.notes.filter { it != note })
                    }

                // Delete linked images
                note.images.forEach { it.delete() }

                // Delete the note (Exposed will remove NoteImageTable entries automatically)
                note.delete()
            }

            AppLogger.m(REMOVE_NOTE_TAG, SimpleResult.SUCCESS)

            return true
        } catch (ex: Exception) {
            AppLogger.m(REMOVE_NOTE_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }

        return false
    }

    /**
     * Gets images for an inventory item
     * @param identifier The identifier of the parent inventory item
     */
    fun getImages(identifier: Uuid): List<StoredImage> {
        // Result list
        val result = mutableListOf<StoredImage>()

        try {
            transaction {
                // Find inventory item
                InventoryEntity.findById(identifier.toJavaUuid())?.let { inventoryEntity ->
                    // Add found images to result, converting them to stored image
                    result.addAll(inventoryEntity.images.map { it.toStoredImage() })

                    AppLogger.m(GET_IMAGES_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(GET_IMAGES_TAG, "Inventory not found!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_IMAGES_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }

        return result
    }

    /**
     * Adds an image to the image table and links it to an inventory item
     * @param identifier The identifier of the parent inventory item
     * @param images List of images to add
     */
    fun addImages(identifier: Uuid, images: List<StoredImage>) {
        try {
            transaction {
                // Find inventory item
                InventoryEntity.findByIdAndUpdate(identifier.toJavaUuid()) { inventoryEntity ->
                    // Add images to table
                    val imageEntities = images.map { storedImage ->
                        ImageEntity.new(storedImage.identifier.toJavaUuid()) {
                            path = storedImage.path
                            time = storedImage.time
                        }
                    }

                    // Add them to inventory
                    inventoryEntity.images = SizedCollection(inventoryEntity.images + imageEntities)

                    AppLogger.m(ADD_IMAGES_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.m(ADD_IMAGES_TAG, "Cound not find inventory!")
            }
        } catch (ex: Exception) {
            AppLogger.i(ADD_IMAGES_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }
    }

    /** Removes an image linked to an inventory item. This will delete it from our image table but WILL NOT delete the image file itself.
     * @param identifier The identifier of the parent inventory item
     * @param images List of images to remove
     */
    fun removeImages(identifier: Uuid, images: List<StoredImage>) {
        try {
            transaction {
                // Find inventory item
                InventoryEntity.findByIdAndUpdate(identifier.toJavaUuid()) { inventoryEntity ->
                    // Get image entities
                    val imageEntities = images.map { storedImage ->
                        ImageEntity.findById(storedImage.identifier.toJavaUuid())
                    }

                    // Filter the original image list
                    val filtered = inventoryEntity.images.filter { !imageEntities.contains(it) }

                    // Update it
                    inventoryEntity.images = SizedCollection(filtered)

                    // Delete images from table
                    imageEntities.forEach { entity -> entity?.delete() }

                    AppLogger.m(REMOVE_IMAGES_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.m(REMOVE_IMAGES_TAG, "Cound not find inventory!")
            }
        } catch (ex: Exception) {
            AppLogger.i(REMOVE_IMAGES_TAG, SimpleResult.FAIL)
            ex.printStackTrace()
        }
    }
}
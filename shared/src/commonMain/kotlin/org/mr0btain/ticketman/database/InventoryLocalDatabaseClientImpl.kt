package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.toImage
import org.mr0btain.ticketman.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.database.tables.inventory.InventoryNoteTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.inventory.toInventoryItem
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class InventoryLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<Inventory>(
    tag = "InventoryDatabaseClient",
    shouldLog = shouldLog
) {
    /** Gets all items from database */
    override fun getAll(): List<Inventory>? {
        var result = listOf<Inventory>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = InventoryEntity.all().map { item -> item.toInventoryItem() }

                AppLogger.m(GET_ALL_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
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

                AppLogger.m(GET_BY_ID_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(GET_BY_ID_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

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

                AppLogger.m(ADD_ALL_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
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

                AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Adds an item to the database */
    override fun add(data: Inventory): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Convert kotlin uuid to java for database
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

                AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_TAG, SimpleError.FAIL)
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

                AppLogger.m(UPDATE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(UPDATE_TAG, SimpleError.FAIL)
            return false
        }
    }

    override fun addNote(identifier: Uuid, note: Note, images: List<StoredImage>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Insert the note into database and get its entity
                val noteEntity = insertNote(note, images)

                // Add note to customer notes
                InventoryEntity.findByIdAndUpdate(identifier.toJavaUuid()) { inventoryEntity ->
                    inventoryEntity.notes = SizedCollection(inventoryEntity.notes + noteEntity)
                }

                AppLogger.m(ADD_NOTE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (ex: Exception) {
            AppLogger.i(ADD_NOTE_TAG, SimpleError.FAIL)
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
                        result[noteEntity.toNote()] = noteEntity.images.map { it.toImage() }
                    }

                    AppLogger.m(GET_NOTES_TAG, SimpleError.SUCCESS)
                } ?: AppLogger.i(GET_NOTES_TAG, "Could not find inventory item!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_NOTES_TAG, SimpleError.FAIL)
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

            AppLogger.m(REMOVE_NOTE_TAG, SimpleError.SUCCESS)

            return true
        } catch (ex: Exception) {
            AppLogger.m(REMOVE_NOTE_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return false
    }
}
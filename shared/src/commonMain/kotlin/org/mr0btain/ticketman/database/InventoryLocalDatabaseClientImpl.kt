package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.image.Image
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.database.tables.inventory.toInventoryItem
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class InventoryLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<InventoryItem>(
    tag = "InventoryDatabaseClient",
    shouldLog = shouldLog
) {
    /** Gets all items from database */
    override fun getAll(): List<InventoryItem>? {
        var result = listOf<InventoryItem>()

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
    override fun getById(identifier: Uuid): InventoryItem? {
        var result: InventoryItem? = null

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
    override fun addAll(data: List<InventoryItem>): Boolean {
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
    override fun add(data: InventoryItem): Boolean {
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
    override fun update(data: InventoryItem): Boolean {
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

    override fun addNote(identifier: Uuid, note: Note, images: List<Image>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeNote(identifier: Uuid): Boolean {
        TODO("Not yet implemented")
    }
}
package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.database.tables.InventoryTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class InventoryLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<InventoryItem>(
    tag = "InventoryDatabaseClient",
    table = InventoryTable,
    shouldLog = shouldLog
) {
    override val IdentifierColumn = InventoryTable.identifier

    /** Gets all items from database */
    override fun getAll(): List<InventoryItem>? {
        var result = listOf<InventoryItem>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = InventoryTable.selectAll().map { item ->
                    InventoryItem(
                        identifier = item[InventoryTable.identifier],
                        name = item[InventoryTable.name],
                        description = item[InventoryTable.description],
                        stock = item[InventoryTable.stock],
                        cost = item[InventoryTable.cost],
                        price = item[InventoryTable.price],
                        notes = item[InventoryTable.notes].notes,
                        time = item[InventoryTable.time]
                    )
                }

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
    override fun getById(identifier: String): InventoryItem? {
        var result: InventoryItem? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = InventoryTable.selectAll().where { IdentifierColumn eq identifier }.map { item ->
                    InventoryItem(
                        identifier = item[InventoryTable.identifier],
                        name = item[InventoryTable.name],
                        description = item[InventoryTable.description],
                        stock = item[InventoryTable.stock],
                        cost = item[InventoryTable.cost],
                        price = item[InventoryTable.price],
                        notes = item[InventoryTable.notes].notes,
                        time = item[InventoryTable.time]
                    )
                }.firstOrNull()

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

                InventoryTable.batchInsert(data) { item ->
                    this[InventoryTable.identifier] = item.identifier
                    this[InventoryTable.name] = item.name
                    this[InventoryTable.description] = item.description
                    this[InventoryTable.stock] = item.stock
                    this[InventoryTable.cost] = item.cost
                    this[InventoryTable.price] = item.price
                    this[InventoryTable.notes] = Notes(item.notes)
                    this[InventoryTable.time] = item.time
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
    override fun remove(id: String): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)
                InventoryTable.deleteWhere { IdentifierColumn eq id }
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

                InventoryTable.insert { item ->
                    item[InventoryTable.identifier] = data.identifier
                    item[InventoryTable.name] = data.name
                    item[InventoryTable.description] = data.description
                    item[InventoryTable.stock] = data.stock
                    item[InventoryTable.cost] = data.cost
                    item[InventoryTable.price] = data.price
                    item[InventoryTable.notes] = Notes(data.notes)
                    item[InventoryTable.time] = data.time
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

                InventoryTable.update({ IdentifierColumn eq data.identifier }) { item ->
                    item[InventoryTable.identifier] = data.identifier
                    item[InventoryTable.name] = data.name
                    item[InventoryTable.description] = data.description
                    item[InventoryTable.stock] = data.stock
                    item[InventoryTable.cost] = data.cost
                    item[InventoryTable.price] = data.price
                    item[InventoryTable.notes] = Notes(data.notes)
                    item[InventoryTable.time] = data.time
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
}
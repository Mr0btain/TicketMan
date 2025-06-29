package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.database.tables.InventoryTable

class InventoryDatabase(
    private val shouldLog: Boolean
) {
    fun getAll(): List<InventoryItem>? {
        var result = listOf<InventoryItem>()

        try {
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
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun addAll(items: List<InventoryItem>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Batch insert
                InventoryTable.batchInsert(items) { item ->
                    this[InventoryTable.identifier] = item.identifier
                    this[InventoryTable.name] = item.name
                    this[InventoryTable.description] = item.description
                    this[InventoryTable.stock] = item.stock
                    this[InventoryTable.cost] = item.cost
                    this[InventoryTable.price] = item.price
                    this[InventoryTable.notes] = Notes(item.notes)
                    this[InventoryTable.time] = item.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun remove(id: String): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Delete employee
                InventoryTable.deleteWhere { identifier eq id }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun getNextId(): String? {
        var result = 0

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                InventoryTable.select(InventoryTable.identifier).forEach { item ->
                    if (item[InventoryTable.identifier].toInt() > result) result = item[InventoryTable.identifier].toInt()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return (result + 1).toString()
    }

    fun add(inventoryItem: InventoryItem): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                InventoryTable.insert { item ->
                    item[InventoryTable.identifier] = inventoryItem.identifier
                    item[InventoryTable.name] = inventoryItem.name
                    item[InventoryTable.description] = inventoryItem.description
                    item[InventoryTable.stock] = inventoryItem.stock
                    item[InventoryTable.cost] = inventoryItem.cost
                    item[InventoryTable.price] = inventoryItem.price
                    item[InventoryTable.notes] = Notes(inventoryItem.notes)
                    item[InventoryTable.time] = inventoryItem.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun update(inventoryItem: InventoryItem): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                InventoryTable.update(where = { InventoryTable.identifier eq inventoryItem.identifier }) { item ->
                    item[InventoryTable.identifier] = inventoryItem.identifier
                    item[InventoryTable.name] = inventoryItem.name
                    item[InventoryTable.description] = inventoryItem.description
                    item[InventoryTable.stock] = inventoryItem.stock
                    item[InventoryTable.cost] = inventoryItem.cost
                    item[InventoryTable.price] = inventoryItem.price
                    item[InventoryTable.notes] = Notes(inventoryItem.notes)
                    item[InventoryTable.time] = inventoryItem.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }
}
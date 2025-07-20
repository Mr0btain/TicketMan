package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.database.tables.TicketDataTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class TicketLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<TicketInfo>(
    tag = "TicketLocalDatabaseClient",
    table = TicketDataTable,
    shouldLog = shouldLog
) {
    override val IdentifierColumn = TicketDataTable.identifier

    /** Gets all items from database */
    override fun getAll(): List<TicketInfo>? {
        var result = listOf<TicketInfo>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = TicketDataTable.selectAll().map { item ->
                    TicketInfo(
                        identifier = item[TicketDataTable.identifier],
                        customerId = item[TicketDataTable.customerId],
                        device = item[TicketDataTable.device],
                        assignedUser = item[TicketDataTable.assignedUser],
                        priority = item[TicketDataTable.priority],
                        status = item[TicketDataTable.status],
                        updates = item[TicketDataTable.notes].notes,
                        time = item[TicketDataTable.time]
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
    override fun getById(identifier: String): TicketInfo? {
        var result: TicketInfo? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = TicketDataTable.selectAll().where { IdentifierColumn eq identifier }.map { item ->
                    TicketInfo(
                        identifier = item[TicketDataTable.identifier],
                        customerId = item[TicketDataTable.customerId],
                        device = item[TicketDataTable.device],
                        assignedUser = item[TicketDataTable.assignedUser],
                        priority = item[TicketDataTable.priority],
                        status = item[TicketDataTable.status],
                        updates = item[TicketDataTable.notes].notes,
                        time = item[TicketDataTable.time]
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
    override fun addAll(data: List<TicketInfo>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketDataTable.batchInsert(data) { item ->
                    this[TicketDataTable.identifier] = item.identifier
                    this[TicketDataTable.customerId] = item.customerId
                    this[TicketDataTable.device] = item.device
                    this[TicketDataTable.assignedUser] = item.assignedUser
                    this[TicketDataTable.priority] = item.priority
                    this[TicketDataTable.status] = item.status
                    this[TicketDataTable.notes] = Notes(item.updates)
                    this[TicketDataTable.time] = item.time
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

                TicketDataTable.deleteWhere { IdentifierColumn eq id }
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
    override fun add(data: TicketInfo): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketDataTable.insert { item ->
                    item[TicketDataTable.identifier] = data.identifier
                    item[TicketDataTable.customerId] = data.customerId
                    item[TicketDataTable.device] = data.device
                    item[TicketDataTable.assignedUser] = data.assignedUser
                    item[TicketDataTable.priority] = data.priority
                    item[TicketDataTable.status] = data.status
                    item[TicketDataTable.notes] = Notes(data.updates)
                    item[TicketDataTable.time] = data.time
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
    override fun update(data: TicketInfo): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketDataTable.update({ IdentifierColumn eq data.identifier }) { item ->
                    item[TicketDataTable.identifier] = data.identifier
                    item[TicketDataTable.customerId] = data.customerId
                    item[TicketDataTable.device] = data.device
                    item[TicketDataTable.assignedUser] = data.assignedUser
                    item[TicketDataTable.priority] = data.priority
                    item[TicketDataTable.status] = data.status
                    item[TicketDataTable.notes] = Notes(data.updates)
                    item[TicketDataTable.time] = data.time
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
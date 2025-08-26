package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.database.tables.TicketTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class TicketLocalDatabaseClientImpl(
    private val shouldLog: Boolean = true
) : LocalDatabaseClient<Ticket>(
    tag = "TicketLocalDatabaseClient",
    table = TicketTable,
    shouldLog = shouldLog
) {
    override val IdentifierColumn = TicketTable.identifier

    /** Gets all items from database */
    override fun getAll(): List<Ticket>? {
        var result = listOf<Ticket>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = TicketTable.selectAll().map { item ->
                    Ticket(
                        identifier = item[TicketTable.identifier],
                        customerId = item[TicketTable.customerId],
                        device = item[TicketTable.device],
                        assignedUser = item[TicketTable.assignedUser],
                        priority = item[TicketTable.priority],
                        status = item[TicketTable.status],
                        updates = item[TicketTable.updates],
                        time = item[TicketTable.time]
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
    override fun getById(identifier: String): Ticket? {
        var result: Ticket? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = TicketTable.selectAll().where { IdentifierColumn eq identifier }.map { item ->
                    Ticket(
                        identifier = item[TicketTable.identifier],
                        customerId = item[TicketTable.customerId],
                        device = item[TicketTable.device],
                        assignedUser = item[TicketTable.assignedUser],
                        priority = item[TicketTable.priority],
                        status = item[TicketTable.status],
                        updates = item[TicketTable.updates],
                        time = item[TicketTable.time]
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
    override fun addAll(data: List<Ticket>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketTable.batchInsert(data) { item ->
                    this[TicketTable.identifier] = item.identifier
                    this[TicketTable.customerId] = item.customerId
                    this[TicketTable.device] = item.device
                    this[TicketTable.assignedUser] = item.assignedUser
                    this[TicketTable.priority] = item.priority
                    this[TicketTable.status] = item.status
                    this[TicketTable.updates] = item.updates
                    this[TicketTable.time] = item.time
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

                TicketTable.deleteWhere { IdentifierColumn eq id }
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
    override fun add(data: Ticket): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketTable.insert { item ->
                    item[TicketTable.identifier] = data.identifier
                    item[TicketTable.customerId] = data.customerId
                    item[TicketTable.device] = data.device
                    item[TicketTable.assignedUser] = data.assignedUser
                    item[TicketTable.priority] = data.priority
                    item[TicketTable.status] = data.status
                    item[TicketTable.updates] = data.updates
                    item[TicketTable.time] = data.time
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
    override fun update(data: Ticket): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketTable.update({ IdentifierColumn eq data.identifier }) { item ->
                    item[TicketTable.identifier] = data.identifier
                    item[TicketTable.customerId] = data.customerId
                    item[TicketTable.device] = data.device
                    item[TicketTable.assignedUser] = data.assignedUser
                    item[TicketTable.priority] = data.priority
                    item[TicketTable.status] = data.status
                    item[TicketTable.updates] = data.updates
                    item[TicketTable.time] = data.time
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
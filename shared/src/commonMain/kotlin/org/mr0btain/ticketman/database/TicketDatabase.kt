package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.tables.TicketDataTable

class TicketDatabase(
    private val shouldLog: Boolean
) {
    fun getAll(): List<TicketInfo>? {
        var result = listOf<TicketInfo>()

        try {
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
                        updates = item[TicketDataTable.updates].notes,
                        time = item[TicketDataTable.time]
                    )
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun addAll(items: List<TicketInfo>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Batch insert
                TicketDataTable.batchInsert(items) { item ->
                    this[TicketDataTable.identifier] = item.identifier
                    this[TicketDataTable.customerId] = item.customerId
                    this[TicketDataTable.device] = item.device
                    this[TicketDataTable.assignedUser] = item.assignedUser
                    this[TicketDataTable.priority] = item.priority
                    this[TicketDataTable.status] = item.status
                    this[TicketDataTable.updates] = Notes(item.updates)
                    this[TicketDataTable.time] = item.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun remove(id: Int): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Delete item
                TicketDataTable.deleteWhere { identifier eq id }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getNextId(): Int? {
        var result = 0

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketDataTable.select(TicketDataTable.identifier).forEach { item ->
                    if (item[TicketDataTable.identifier] > result) result = item[TicketDataTable.identifier]
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return (result + 1)
    }

    fun add(data: TicketInfo): Boolean {
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
                    item[TicketDataTable.updates] = Notes(data.updates)
                    item[TicketDataTable.time] = data.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    fun update(data: TicketInfo): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketDataTable.update({ TicketDataTable.identifier eq data.identifier }) { item ->
                    item[TicketDataTable.identifier] = data.identifier
                    item[TicketDataTable.customerId] = data.customerId
                    item[TicketDataTable.device] = data.device
                    item[TicketDataTable.assignedUser] = data.assignedUser
                    item[TicketDataTable.priority] = data.priority
                    item[TicketDataTable.status] = data.status
                    item[TicketDataTable.updates] = Notes(data.updates)
                    item[TicketDataTable.time] = data.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }
}
package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.customer.TicketList
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.database.tables.*

/**
 * Handles interaction between a local database
 */
class LocalDatabase<T : MainDataItem>(
    private val table: DataTable,
    private val shouldLog: Boolean
) {
    /** Gets all items from database */
    @Suppress("UNCHECKED_CAST")
    fun getAll(): List<T>? {
        var result = listOf<T>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = databaseGetAll(table) as List<T>
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    /** Adds all items to the database */
    fun addAll(data: List<T>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                data.forEach { databaseInsert(it, table) }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /** Removes an item from the database */
    fun remove(id: String): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                val column = table.identifier()

                // Delete item
                if (column != null) {
                    val _table = table as IntIdTable

                    table.deleteWhere { column eq id }
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /** Gets the next identifier */
    fun getNextId(): String? {
        var result = 0
        var error = false

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Get proper column
                val column = table.identifier()

                // TODO: Make sure this works without mapping it to int
                // Go through and get the highest number
                if (column != null) {
                    table.getTableType()?.select(column)?.map { it[column] }?.let { list ->
                        list.maxOfOrNull { it }?.let {
                        result = it.toInt()
                        } ?: { error = true }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return if (error) null else (result + 1).toString()
    }

    /** Adds an item to the database */
    fun add(data: T): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                databaseInsert(data, table)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /** Updates an item in the database */
    fun update(data: T): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                databaseUpdate(data, table)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

private fun DataTable.getTableType(): IntIdTable? {
    return when (this) {
        is CustomerDataTable -> this
        is TicketDataTable -> this
        is InventoryTable -> this
        is UserTable -> this
        else -> null
    }
}

/** gets the column depending on the type of table */
private fun DataTable.identifier(): Column<String>? {
    return when (this) {
        is CustomerDataTable -> this.identifier
        is TicketDataTable -> this.identifier
        is InventoryTable -> this.identifier
        else -> null
    }
}

private fun databaseGetAll(table: DataTable): List<MainDataItem> {
    return when (table) {
        is CustomerDataTable -> {
            table.selectAll().map { item ->
                CustomerData(
                    identifier = item[CustomerDataTable.identifier],
                    info = CustomerInfo(
                        identifier = item[CustomerDataTable.identifier],
                        name = PersonName(
                            firstName = item[CustomerDataTable.firstName],
                            lastName = item[CustomerDataTable.lastName]
                        ),
                        address = Address(
                            street = item[CustomerDataTable.street],
                            city = item[CustomerDataTable.city],
                            state = item[CustomerDataTable.state],
                            zip = item[CustomerDataTable.zip]
                        ),
                        contactInfo = item[CustomerDataTable.contactInfo],
                        time = item[CustomerDataTable.time]
                    ),
                    tickets = item[CustomerDataTable.tickets].list,
                    notes = item[CustomerDataTable.notes].notes
                )
            }
        }

        is TicketDataTable -> {
            table.selectAll().map { item ->
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

        is InventoryTable -> {
            table.selectAll().map { item ->
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

        is UserTable -> {
            table.selectAll().map { item ->
                User(
                    identifier = item[UserTable.identifier],
                    username = item[UserTable.username],
                    password = item[UserTable.password],
                    name = PersonName(item[UserTable.firstName], item[UserTable.lastName]),
                    notes = item[UserTable.notes].notes,
                    contactInfo = item[UserTable.contactInfo],
                    tickets = item[UserTable.tickets].list,
                    accessLevel = item[UserTable.access],
                    time = item[UserTable.time],
                )
            }
        }

        else -> emptyList()
    }
}

private fun databaseInsert(data: MainDataItem, table: DataTable) {
    when (table) {
        is CustomerDataTable -> {
            if (data is CustomerData) {
                table.insert { item ->
                    item[identifier] = data.identifier
                    item[firstName] = data.info.name.firstName
                    item[lastName] = data.info.name.lastName
                    item[street] = data.info.address.street
                    item[city] = data.info.address.city
                    item[state] = data.info.address.state
                    item[zip] = data.info.address.zip
                    item[tickets] = TicketList(data.tickets)
                    item[notes] = Notes(data.notes)
                    item[contactInfo] = data.info.contactInfo
                    item[time] = data.info.time
                }
            }
        }

        is TicketDataTable -> {

        }

        is InventoryTable -> {
            if (data is InventoryItem) {
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
            }
        }

        is UserTable -> {
            if (data is User) {
                UserTable.insert { item ->
                    item[UserTable.identifier] = data.identifier
                    item[UserTable.username] = data.username
                    item[UserTable.password] = data.password
                    item[UserTable.firstName] = data.name.firstName
                    item[UserTable.lastName] = data.name.lastName
                    item[UserTable.tickets] = TicketList(data.tickets)
                    item[UserTable.contactInfo] = data.contactInfo
                    item[UserTable.notes] = Notes(data.notes)
                    item[UserTable.access] = data.accessLevel
                    item[UserTable.time] = data.time
                }
            }
        }
    }
}

private fun databaseUpdate(data: MainDataItem, table: DataTable) {
    when (table) {
        is CustomerDataTable -> {
            if (data is CustomerData) {
                table.update({ CustomerDataTable.identifier eq data.identifier }) { item ->
                    item[identifier] = data.identifier
                    item[firstName] = data.info.name.firstName
                    item[lastName] = data.info.name.lastName
                    item[street] = data.info.address.street
                    item[city] = data.info.address.city
                    item[state] = data.info.address.state
                    item[zip] = data.info.address.zip
                    item[tickets] = TicketList(data.tickets)
                    item[notes] = Notes(data.notes)
                    item[contactInfo] = data.info.contactInfo
                    item[time] = data.info.time
                }
            }
        }

        is TicketDataTable -> {
            if (data is TicketInfo) {
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
        }

        is InventoryTable -> {
            if (data is InventoryItem) {
                InventoryTable.update(where = { InventoryTable.identifier eq data.identifier }) { item ->
                    item[InventoryTable.identifier] = data.identifier
                    item[InventoryTable.name] = data.name
                    item[InventoryTable.description] = data.description
                    item[InventoryTable.stock] = data.stock
                    item[InventoryTable.cost] = data.cost
                    item[InventoryTable.price] = data.price
                    item[InventoryTable.notes] = Notes(data.notes)
                    item[InventoryTable.time] = data.time
                }
            }
        }

        is UserTable -> {
            TODO("Implement")
        }
    }
}
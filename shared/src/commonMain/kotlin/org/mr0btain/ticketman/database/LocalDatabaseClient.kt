package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.tables.*
import org.mr0btain.ticketman.database.tables.CustomerDataTable.city
import org.mr0btain.ticketman.database.tables.CustomerDataTable.contactInfo
import org.mr0btain.ticketman.database.tables.CustomerDataTable.firstName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.lastName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.notes
import org.mr0btain.ticketman.database.tables.CustomerDataTable.state
import org.mr0btain.ticketman.database.tables.CustomerDataTable.street
import org.mr0btain.ticketman.database.tables.CustomerDataTable.tickets
import org.mr0btain.ticketman.database.tables.CustomerDataTable.time
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Handles interaction between a local database
 */
@Suppress("PrivatePropertyName")
class LocalDatabaseClient<T : MainDataItem>(
    tag: String,
    private val table: DataTable,
    private val shouldLog: Boolean
) {
    private val GET_ALL_TAG = "$tag:GetAll"
    private val GET_BY_ID_TAG = "$tag:GetById"
    private val ADD_ALL_TAG = "$tag:AddAll"
    private val REMOVE_TAG = "$tag:Remove"
    private val GET_NEXT_ID_TAG = "$tag:GetNextId"
    private val ADD_TAG = "$tag:Add"
    private val UPDATE_TAG = "$tag:Update"

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
    @Suppress("UNCHECKED_CAST")
    fun getById(identifier: String): T? {
        var result: T? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result =  databaseGetAll(table).firstOrNull { item -> item.identifier == identifier } as T

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
    fun addAll(data: List<MainDataItem>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                databaseBatchInsert(data, table)

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
    fun remove(id: String): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                val column = table.identifier()

                // Delete item
                if (column != null) {
                    table.getTableType()?.deleteWhere { column eq id }

                    AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
                } else AppLogger.i(REMOVE_TAG, SimpleError.FAIL)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleError.FAIL)
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
                            AppLogger.m(GET_NEXT_ID_TAG, SimpleError.SUCCESS)
                        } ?: {
                            error = true
                            AppLogger.i(GET_NEXT_ID_TAG, SimpleError.FAIL)
                        }
                    } ?: AppLogger.i(GET_NEXT_ID_TAG, SimpleError.FAIL)
                } else AppLogger.m(GET_NEXT_ID_TAG, SimpleError.FAIL)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.m(GET_NEXT_ID_TAG, SimpleError.FAIL)
            return null
        }

        return if (error) null else (result + 1).toString()
    }

    /** Adds an item to the database */
    fun add(data: MainDataItem): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                databaseInsert(data, table)

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
    fun update(data: MainDataItem): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                databaseUpdate(data, table)

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
                            firstName = item[firstName],
                            lastName = item[lastName]
                        ),
                        address = Address(
                            street = item[street],
                            city = item[city],
                            state = item[state],
                            zip = item[CustomerDataTable.zip]
                        ),
                        contactInfo = item[contactInfo],
                        time = item[time]
                    ),
                    tickets = item[tickets].list,
                    notes = item[notes].notes
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
            if (data is TicketInfo) {
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

private fun databaseBatchInsert(data: List<MainDataItem>, table: DataTable) {
    when (table) {
        is CustomerDataTable -> {
            data.filterIsInstance<CustomerData>().let { list ->
                table.batchInsert(list) { item ->
                    this[CustomerDataTable.identifier] = item.identifier
                    this[firstName] = item.info.name.firstName
                    this[lastName] = item.info.name.lastName
                    this[street] = item.info.address.street
                    this[city] = item.info.address.city
                    this[state] = item.info.address.state
                    this[CustomerDataTable.zip] = item.info.address.zip
                    this[tickets] = TicketList(item.tickets)
                    this[notes] = Notes(item.notes)
                    this[contactInfo] = item.info.contactInfo
                    this[time] = item.info.time
                }
            }
        }

        is TicketDataTable -> {
            data.filterIsInstance<TicketInfo>().let { list ->
                TicketDataTable.batchInsert(list) { item ->
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
        }

        is InventoryTable -> {
            data.filterIsInstance<InventoryItem>().let { list ->
                InventoryTable.batchInsert(list) { item ->
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
        }

        is UserTable -> {
            data.filterIsInstance<User>().let { list ->
                UserTable.batchInsert(list) { item ->
                    this[UserTable.identifier] = item.identifier
                    this[UserTable.username] = item.username
                    this[UserTable.password] = item.password
                    this[UserTable.firstName] = item.name.firstName
                    this[UserTable.lastName] = item.name.lastName
                    this[UserTable.tickets] = TicketList(item.tickets)
                    this[UserTable.contactInfo] = item.contactInfo
                    this[UserTable.notes] = Notes(item.notes)
                    this[UserTable.access] = item.accessLevel
                    this[UserTable.time] = item.time
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
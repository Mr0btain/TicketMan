package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.database.tables.CustomerDataTable
import org.mr0btain.ticketman.database.tables.CustomerDataTable.city
import org.mr0btain.ticketman.database.tables.CustomerDataTable.contactInfo
import org.mr0btain.ticketman.database.tables.CustomerDataTable.firstName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.identifier
import org.mr0btain.ticketman.database.tables.CustomerDataTable.lastName
import org.mr0btain.ticketman.database.tables.CustomerDataTable.notes
import org.mr0btain.ticketman.database.tables.CustomerDataTable.state
import org.mr0btain.ticketman.database.tables.CustomerDataTable.street
import org.mr0btain.ticketman.database.tables.CustomerDataTable.tickets
import org.mr0btain.ticketman.database.tables.CustomerDataTable.time
import org.mr0btain.ticketman.database.tables.CustomerDataTable.zip
import org.mr0btain.ticketman.database.tables.DataTable
import org.mr0btain.ticketman.database.tables.InventoryTable
import org.mr0btain.ticketman.database.tables.TicketDataTable
import org.mr0btain.ticketman.database.tables.UserTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class CustomerLocalDatabaseClientImpl(
    private val shouldLog: Boolean
) : LocalDatabaseClient {
    override val TAG = "CustomerLocalDatabaseClient"
    override val GET_ALL_TAG = "$TAG:GetAll"
    override val GET_BY_ID_TAG = "$TAG:GetById"
    override val ADD_ALL_TAG = "$TAG:AddAll"
    override val REMOVE_TAG = "$TAG:Remove"
    override val GET_NEXT_ID_TAG = "$TAG:GetNextId"
    override val ADD_TAG = "$TAG:Add"
    override val UPDATE_TAG = "$TAG:Update"

    /** Gets all items from database */
    override fun getAll(): List<CustomerData>? {
        var result = listOf<CustomerData>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = CustomerDataTable.selectAll().map { item ->
                    CustomerData(
                        identifier = item[identifier],
                        info = CustomerInfo(
                            identifier = item[identifier],
                            name = PersonName(
                                firstName = item[firstName],
                                lastName = item[lastName]
                            ),
                            address = Address(
                                street = item[street],
                                city = item[city],
                                state = item[state],
                                zip = item[zip]
                            ),
                            contactInfo = item[contactInfo],
                            time = item[time]
                        ),
                        tickets = item[tickets].list,
                        notes = item[notes].notes
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
    override fun getById(identifier: String): CustomerData? {
        var result: CustomerData? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = CustomerDataTable.selectAll().where { CustomerDataTable.identifier eq identifier }.map { item ->
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
                                zip = item[zip]
                            ),
                            contactInfo = item[contactInfo],
                            time = item[time]
                        ),
                        tickets = item[tickets].list,
                        notes = item[notes].notes
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
    override fun addAll(data: List<MainDataItem>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable.batchInsert(data.filterIsInstance<CustomerData>()) { item ->
                    this[identifier] = item.identifier
                    this[firstName] = item.info.name.firstName
                    this[lastName] = item.info.name.lastName
                    this[street] = item.info.address.street
                    this[city] = item.info.address.city
                    this[state] = item.info.address.state
                    this[zip] = item.info.address.zip
                    this[tickets] = TicketList(item.tickets)
                    this[notes] = Notes(item.notes)
                    this[contactInfo] = item.info.contactInfo
                    this[time] = item.info.time
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
                CustomerDataTable.deleteWhere { identifier eq id }
                AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Gets the next identifier */
    override fun getNextId(): String? {
        var result = 0
        var error = false

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable
                    .select(identifier)
                    .map { it[identifier] }
                    .let { list ->
                        list.maxOfOrNull { it }?.let {
                            result = it.toInt()
                            AppLogger.m(GET_NEXT_ID_TAG, SimpleError.SUCCESS)
                        } ?: {
                            error = true
                            AppLogger.i(GET_NEXT_ID_TAG, SimpleError.FAIL)
                        }
                    }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.m(GET_NEXT_ID_TAG, SimpleError.FAIL)
            return null
        }

        return if (error) null else (result + 1).toString()
    }

    /** Adds an item to the database */
    override fun add(data: MainDataItem): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                if (data is CustomerData) {
                    CustomerDataTable.insert { item ->
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

                    AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
                } else AppLogger.m(ADD_TAG, SimpleError.FAIL)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Updates an item in the database */
    override fun update(data: MainDataItem): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                if (data is CustomerData) {
                    CustomerDataTable.update({ identifier eq data.identifier }) { item ->
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

                    AppLogger.m(UPDATE_TAG, SimpleError.SUCCESS)
                } else AppLogger.m(UPDATE_TAG, SimpleError.FAIL)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(UPDATE_TAG, SimpleError.FAIL)
            return false
        }
    }
}

private fun databaseGetAll(table: DataTable): List<MainDataItem> {
    return when (table) {

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
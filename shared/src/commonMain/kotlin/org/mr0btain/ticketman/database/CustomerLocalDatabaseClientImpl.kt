package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.customer.Address
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
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class CustomerLocalDatabaseClientImpl(
    private val shouldLog: Boolean
) : LocalDatabaseClient<CustomerData>(
    tag = "CustomerLocalDatabaseClient",
    table = CustomerDataTable,
    shouldLog = shouldLog
) {
    override val IdentifierColumn = CustomerDataTable.identifier

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
                        identifier = item[IdentifierColumn],
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
                        time = item[time],
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

                result = CustomerDataTable.selectAll().where { IdentifierColumn eq identifier }.map { item ->
                    CustomerData(
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
                        time = item[time],
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
    override fun addAll(data: List<CustomerData>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable.batchInsert(data) { item ->
                    this[identifier] = item.identifier
                    this[firstName] = item.name.firstName
                    this[lastName] = item.name.lastName
                    this[street] = item.address.street
                    this[city] = item.address.city
                    this[state] = item.address.state
                    this[zip] = item.address.zip
                    this[tickets] = TicketList(item.tickets)
                    this[notes] = Notes(item.notes)
                    this[contactInfo] = item.contactInfo
                    this[time] = item.time
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
                CustomerDataTable.deleteWhere { IdentifierColumn eq id }
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
    override fun add(data: CustomerData): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable.insert { item ->
                    item[identifier] = data.identifier
                    item[firstName] = data.name.firstName
                    item[lastName] = data.name.lastName
                    item[street] = data.address.street
                    item[city] = data.address.city
                    item[state] = data.address.state
                    item[zip] = data.address.zip
                    item[tickets] = TicketList(data.tickets)
                    item[notes] = Notes(data.notes)
                    item[contactInfo] = data.contactInfo
                    item[time] = data.time
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
    override fun update(data: CustomerData): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable.update({ IdentifierColumn eq data.identifier }) { item ->
                    item[identifier] = data.identifier
                    item[firstName] = data.name.firstName
                    item[lastName] = data.name.lastName
                    item[street] = data.address.street
                    item[city] = data.address.city
                    item[state] = data.address.state
                    item[zip] = data.address.zip
                    item[tickets] = TicketList(data.tickets)
                    item[notes] = Notes(data.notes)
                    item[contactInfo] = data.contactInfo
                    item[time] = data.time

                    AppLogger.m(UPDATE_TAG, SimpleError.SUCCESS)
                    }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(UPDATE_TAG, SimpleError.FAIL)
            return false
        }
    }
}
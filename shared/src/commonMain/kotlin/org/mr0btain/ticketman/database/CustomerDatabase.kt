package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.customer.TicketList
import org.mr0btain.ticketman.data.shared.Notes
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.database.tables.CustomerDataTable

class CustomerDatabase(
    private val shouldLog: Boolean
) {
    fun getAll(): List<CustomerData>? {
        var result = listOf<CustomerData>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = CustomerDataTable.selectAll().map { item ->
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
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return result
    }

    fun addAll(data: List<CustomerData>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                data.forEach { customer ->
                    CustomerDataTable.insert { item ->
                        item[identifier] = customer.info.identifier
                        item[firstName] = customer.info.name.firstName
                        item[lastName] = customer.info.name.lastName
                        item[street] = customer.info.address.street
                        item[city] = customer.info.address.city
                        item[state] = customer.info.address.state
                        item[zip] = customer.info.address.zip
                        item[tickets] = TicketList(customer.tickets)
                        item[notes] = Notes(customer.notes)
                        item[contactInfo] = customer.info.contactInfo
                        item[time] = customer.info.time
                    }
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
                CustomerDataTable.deleteWhere { identifier eq id }
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

                CustomerDataTable.select(CustomerDataTable.identifier).forEach { item ->
                    if (item[CustomerDataTable.identifier] > result) result = item[CustomerDataTable.identifier]
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return (result + 1)
    }

    fun add(data: CustomerData): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable.insert { item ->
                    item[identifier] = data.info.identifier
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

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun update(data: CustomerData): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerDataTable.update(where = { CustomerDataTable.identifier eq data.info.identifier }) { item ->
                    item[identifier] = data.info.identifier
                    item[firstName] = data.info.name.firstName
                    item[lastName] = data.info.name.lastName
                    item[street] = data.info.address.street
                    item[city] = data.info.address.city
                    item[state] = data.info.address.state
                    item[zip] = data.info.address.zip
                    item[tickets] = TicketList(data.tickets)
                    item[notes] = Notes(data.notes)
                    item[contactInfo] = contactInfo
                    item[time] = data.info.time
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
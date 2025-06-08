package org.mr0btain.ticketman.components.data.database

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.data.components.*
import org.mr0btain.ticketman.components.data.components.customer.Address
import org.mr0btain.ticketman.components.data.components.customer.CustomerData
import org.mr0btain.ticketman.components.data.components.customer.CustomerInfo
import org.mr0btain.ticketman.components.data.components.customer.TicketList
import org.mr0btain.ticketman.components.data.components.employee.Employee
import org.mr0btain.ticketman.components.data.components.inventory.InventoryItem
import org.mr0btain.ticketman.components.data.components.shared.Notes
import org.mr0btain.ticketman.components.data.components.shared.PersonName
import org.mr0btain.ticketman.components.data.components.ticket.TicketInfo
import org.mr0btain.ticketman.components.data.database.tables.CustomerDataTable
import org.mr0btain.ticketman.components.data.database.tables.EmployeeTable
import org.mr0btain.ticketman.components.data.database.tables.InventoryTable
import org.mr0btain.ticketman.components.data.database.tables.TicketDataTable

class SqliteDb() {
    // TODO: Flesh these out better to support copying syncing and such between dbs
    // TODO: Add encryption
    // TODO: Make more functions for indexing and searching the db for things
    // TODO: FInd better db for android. really would like to use SQLite

    // Get the app directory depending on platform
    private val appDir = FileKit.filesDir.path

    // Connect to the local database
    private val db = Database.connect("jdbc:h2:$appDir/data/testData;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    //Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
    // Database.connect("jdbc:h2:$appDir/testData.db", "org.h2.Driver")
    //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    // Should we Log or not.
    // TODO: Make this a setting variable?
    private val shouldLog = true

    /** ------------------------------------------ Customer ---------------------------------------------------- */
    fun getAllCustomers(): List<CustomerData> {
        var result = listOf<CustomerData>()

        // Go into database and convert data to customer data item
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            result = CustomerDataTable.selectAll().map { item ->
                CustomerData(
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

        return result
    }

    fun addAllCustomers(data: List<CustomerData>) {
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
    }

    fun removeCustomer(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete item
            CustomerDataTable.deleteWhere { identifier eq id }
        }
    }

    fun getNextCustomerId(): Int {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.select(CustomerDataTable.identifier).forEach { item ->
                if (item[CustomerDataTable.identifier] > result) result = item[CustomerDataTable.identifier]
            }
        }

        return (result + 1)
    }

    fun addCustomer(data: CustomerData) {
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
    }

    fun updateCustomer(data: CustomerData) {
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
    }


    /** ------------------------------------------ Ticket ---------------------------------------------------- */

    fun getAllTickets(): List<TicketInfo> {
        var result = listOf<TicketInfo>()

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            result = TicketDataTable.selectAll().map { item ->
                TicketInfo(
                    identifier = item[TicketDataTable.identifier],
                    customerId = item[TicketDataTable.customerId],
                    device = item[TicketDataTable.device],
                    assignedEmployee = item[TicketDataTable.assignedEmp],
                    priority = item[TicketDataTable.priority],
                    status = item[TicketDataTable.status],
                    updates = item[TicketDataTable.updates].notes,
                    time = item[TicketDataTable.time]
                )
            }
        }

        return result
    }

    fun addAllTickets(items: List<TicketInfo>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Batch insert
            TicketDataTable.batchInsert(items) { item ->
                this[TicketDataTable.identifier] = item.identifier
                this[TicketDataTable.customerId] = item.customerId
                this[TicketDataTable.device] = item.device
                this[TicketDataTable.assignedEmp] = item.assignedEmployee
                this[TicketDataTable.priority] = item.priority
                this[TicketDataTable.status] = item.status
                this[TicketDataTable.updates] = Notes(item.updates)
                this[TicketDataTable.time] = item.time
            }
        }
    }

    fun removeTicket(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete item
            TicketDataTable.deleteWhere { identifier eq id }
        }
    }

    fun getNextTicketId(): Int {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.select(TicketDataTable.identifier).forEach { item ->
                if (item[TicketDataTable.identifier] > result) result = item[TicketDataTable.identifier]
            }
        }

        return (result + 1)
    }

    fun addTicket(data: TicketInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.insert { item ->
                item[TicketDataTable.identifier] = data.identifier
                item[TicketDataTable.customerId] = data.customerId
                item[TicketDataTable.device] = data.device
                item[TicketDataTable.assignedEmp] = data.assignedEmployee
                item[TicketDataTable.priority] = data.priority
                item[TicketDataTable.status] = data.status
                item[TicketDataTable.updates] = Notes(data.updates)
                item[TicketDataTable.time] = data.time
            }
        }
    }

    fun updateTicket(data: TicketInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq data.identifier }) { item ->
                item[TicketDataTable.identifier] = data.identifier
                item[TicketDataTable.customerId] = data.customerId
                item[TicketDataTable.device] = data.device
                item[TicketDataTable.assignedEmp] = data.assignedEmployee
                item[TicketDataTable.priority] = data.priority
                item[TicketDataTable.status] = data.status
                item[TicketDataTable.updates] = Notes(data.updates)
                item[TicketDataTable.time] = data.time
            }
        }
    }

    /** ------------------------------------------ Employee ---------------------------------------------------- */

    fun getAllEmployees(): List<Employee> {
        val result = mutableListOf<Employee>()

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.selectAll().forEach { item ->
                result.add(
                    Employee(
                        identifier = item[EmployeeTable.identifier],
                        name = PersonName(item[EmployeeTable.firstName], item[EmployeeTable.lastName]),
                        notes = item[EmployeeTable.notes].notes,
                        contactInfo = item[EmployeeTable.contactInfo],
                        tickets = item[EmployeeTable.tickets].list,
                        accessLevel = item[EmployeeTable.access],
                        time = item[EmployeeTable.time]
                    )
                )
            }
        }

        return result
    }

    fun addAllEmployees(items: List<Employee>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Batch insert
            EmployeeTable.batchInsert(items) { item ->
                this[EmployeeTable.identifier] = item.identifier
                this[EmployeeTable.firstName] = item.name.firstName
                this[EmployeeTable.lastName] = item.name.lastName
                this[EmployeeTable.contactInfo] = item.contactInfo
                this[EmployeeTable.notes] = Notes(item.notes)
                this[EmployeeTable.tickets] = TicketList(item.tickets)
                this[EmployeeTable.access] = item.accessLevel
                this[EmployeeTable.time] = item.time
            }
        }
    }

    fun removeEmployee(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete employee
            EmployeeTable.deleteWhere { identifier eq id }
        }
    }

    fun getNextEmployeeId(): Int {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.select(EmployeeTable.identifier).forEach { item ->
                if (item[EmployeeTable.identifier] > result) result = item[EmployeeTable.identifier]
            }
        }

        return (result + 1)
    }

    fun addEmployee(employee: Employee) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.insert { item ->
                item[EmployeeTable.identifier] = employee.identifier
                item[EmployeeTable.firstName] = employee.name.firstName
                item[EmployeeTable.lastName] = employee.name.lastName
                item[EmployeeTable.contactInfo] = employee.contactInfo
                item[EmployeeTable.tickets] = TicketList(employee.tickets)
                item[EmployeeTable.notes] = Notes(employee.notes)
                item[EmployeeTable.access] = employee.accessLevel
            }
        }
    }

    fun updateEmployee(employee: Employee) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update(where = { EmployeeTable.identifier eq employee.identifier }) { item ->
                item[EmployeeTable.identifier] = employee.identifier
                item[EmployeeTable.firstName] = employee.name.firstName
                item[EmployeeTable.lastName] = employee.name.lastName
                item[EmployeeTable.contactInfo] = employee.contactInfo
                item[EmployeeTable.tickets] = TicketList(employee.tickets)
                item[EmployeeTable.notes] = Notes(employee.notes)
                item[EmployeeTable.access] = employee.accessLevel
            }
        }
    }

    /** ------------------------------------------ Inventory ---------------------------------------------------- */

    fun getInventory(): List<InventoryItem> {
        val result = mutableListOf<InventoryItem>()

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            InventoryTable.selectAll().forEach { item ->
                result.add(
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
                )
            }
        }

        return result
    }

    fun addAllInventory(items: List<InventoryItem>) {
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
    }

    fun removeInventoryItem(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete employee
            InventoryTable.deleteWhere { identifier eq id }
        }
    }

    fun getNextInventoryId(): Int {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            InventoryTable.select(EmployeeTable.identifier).forEach { item ->
                if (item[EmployeeTable.identifier] > result) result = item[EmployeeTable.identifier]
            }
        }

        return (result + 1)
    }

    fun addInventoryItem(inventoryItem: InventoryItem) {
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
    }

    fun updateInventoryItem(inventoryItem: InventoryItem) {
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
    }

    fun checkAndCreateTables() {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            SchemaUtils.create(CustomerDataTable, TicketDataTable, EmployeeTable, InventoryTable)
        }
    }
}
package org.mr0btain.ticketman.components.data.database

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.data.components.*
import org.mr0btain.ticketman.components.data.database.tables.CustomerDataTable
import org.mr0btain.ticketman.components.data.database.tables.EmployeeTable
import org.mr0btain.ticketman.components.data.database.tables.TicketDataTable

class SqliteDb() {
    // TODO: Flesh these out better to support copying syncing and such between dbs
    // TODO: Add encryption
    // TODO: Add removal functions
    // TODO: Make more functions for indexing and searching the db for things

    // Get the app directory depending on platform
    private val appDir = FileKit.filesDir.path

    // Connect to the local database
    private val db = Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
        //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    // Should we Log
    private val shouldLog = true

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

    fun updateCustomerName(id: Int, data: PersonName) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id }) { item ->
                item[firstName] = data.firstName
                item[lastName] = data.lastName
            }
        }
    }

    fun updateCustomerAddress(id: Int, data: Address) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id }) { item ->
                item[street] = data.street
                item[city] = data.city
                item[state] = data.state
                item[zip] = data.zip
            }
        }
    }

    fun updateCustomerContact(id: Int, data: ContactInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id }) { item ->
                item[contactInfo] = data
            }
        }
    }

    fun updateCustomerTickets(id: Int, data: List<Int>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id }) { item ->
                item[tickets] = TicketList(data)
            }
        }
    }

    fun updateCustomerNotes(id: Int, data: List<Note>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id }) { item ->
                item[notes] = Notes(data)
            }
        }
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

    fun removeCustomer(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete item
            CustomerDataTable.deleteWhere { identifier eq id }
        }
    }

    fun checkIfCustomerExistsInDb(id: Int): Boolean {
        return CustomerDataTable.select(CustomerDataTable.identifier).where { CustomerDataTable.identifier eq id }.count() > 0
    }

    fun getAllCustomerData(): List<CustomerData> {
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

    fun addAllCustomerData(data: List<CustomerData>) {
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

    fun updateTicketUpdates(id: Int, updates: Notes) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id }) { item ->
                item[TicketDataTable.updates] = updates
            }
        }
    }

    fun updateTicketStatus(id: Int, status: TicketStatus) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id }) { item ->
                item[TicketDataTable.status] = status
            }
        }
    }

    fun updateTicketPriority(id: Int, priority: TicketPriority) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id }) { item ->
                item[TicketDataTable.priority] = priority
            }
        }
    }

    fun updateTicketEmply(id: Int, employeeNum: Int, employeeName: PersonName) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id }) { item ->
                item[TicketDataTable.assignedEmp] = employeeNum
            }
        }
    }

    fun updateTicketDevice(id: Int, device: Device) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id }) { item ->
                item[TicketDataTable.device] = device
            }
        }
    }

    fun updateTicketCustId(id: Int, customerId: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id }) { item ->
                item[TicketDataTable.customerId] = customerId
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

    fun removeTicket(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete item
            TicketDataTable.deleteWhere { identifier eq id }
        }
    }

    fun addAllTicketData(data: List<TicketInfo>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Batch insert
            TicketDataTable.batchInsert(data) { item ->
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

    fun getAllTicketData(): List<TicketInfo> {
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

    fun addEmployee(emp: Employee) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.insert { item ->
                item[EmployeeTable.identifier] = emp.identifier
                item[EmployeeTable.firstName] = emp.name.firstName
                item[EmployeeTable.lastName] = emp.name.lastName
                item[EmployeeTable.contactInfo] = emp.contactInfo
                item[EmployeeTable.tickets] = TicketList(emp.tickets)
                item[EmployeeTable.notes] = Notes(emp.notes)
                item[EmployeeTable.access] = emp.accessLevel
            }
        }
    }

    fun getEmployee(id: Int): Employee {
        var result = Employee()

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            result = EmployeeTable.selectAll().where { EmployeeTable.identifier eq id }.map { item ->
                Employee(
                    identifier = item[EmployeeTable.identifier],
                    name = PersonName(item[EmployeeTable.firstName], item[EmployeeTable.lastName]),
                    notes = item[EmployeeTable.notes].notes,
                    contactInfo = item[EmployeeTable.contactInfo],
                    tickets = item[EmployeeTable.tickets].list,
                    accessLevel = item[EmployeeTable.access],
                    time = item[EmployeeTable.time]
                )
            }.singleOrNull() ?: Employee()
        }

        return result
    }

    fun removeEmployee(id: Int) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete employee
            EmployeeTable.deleteWhere { identifier eq id }
        }
    }

    fun addAllEmployees(employees: List<Employee>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Batch insert
            EmployeeTable.batchInsert(employees) { item ->
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

    fun updateEmployeeName(id: Int, name: PersonName) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id }) { item ->
                item[EmployeeTable.firstName] = name.firstName
                item[EmployeeTable.lastName] = name.lastName
            }
        }
    }

    fun updateEmployeeTickets(id: Int, tickets: List<Int>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id }) { item ->
                item[EmployeeTable.tickets] = TicketList(tickets)
            }
        }
    }

    fun updateEmployeeContactInfo(id: Int, contact: ContactInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id }) { item ->
                item[contactInfo] = contact
            }
        }
    }

    fun updateEmployeeNotes(id: Int, note: List<Note>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id }) { item ->
                item[notes] = Notes(note)
            }
        }
    }

    fun updateEmployeeAccess(id: Int, accessLevel: AccessLevel) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id }) { item ->
                item[access] = accessLevel
            }
        }
    }

    fun checkAndCreateTables() {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            if (!SchemaUtils.listTables().containsAll(listOf("customer", "tickets", "employee"))) {
                SchemaUtils.create(CustomerDataTable, TicketDataTable, EmployeeTable)
            }
        }
    }

    fun listTables(): List<String> {
        var result = listOf<String>()

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            result = SchemaUtils.listTables()
        }

        return result
    }
}


// Connect to a db
// Database.connect("jdbc:sqlite:/data/data.db", "org.sqlite.JDBC")

// in memory
// Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

// TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
//// or Connection.TRANSACTION_READ_UNCOMMITTED
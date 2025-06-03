package org.mr0btain.ticketman.components.data.database

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.locate
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.Tasks
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.components.*
import org.mr0btain.ticketman.components.data.database.tables.CustomerDataTable
import org.mr0btain.ticketman.components.data.database.tables.EmployeeTable
import org.mr0btain.ticketman.components.data.database.tables.TicketDataTable

class SqliteDb() {
    // TODO: Setup error handling
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

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq data.info.identifier.toInt() }) { item ->
                item[identifier] = data.info.identifier.toInt()
                item[firstName] = data.info.name.firstName
                item[lastName] = data.info.name.lastName
                item[street] = data.info.address.street
                item[city] = data.info.address.city
                item[state] = data.info.address.state
                item[zip] = data.info.address.zip
                item[tickets] = TicketList(data.tickets)
                item[notes] = Notes(data.notes)
                item[contactInfo] = contactInfo
            }
        }
    }

    fun getNextCustomerId(): String {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.select(CustomerDataTable.identifier).forEach { item ->
                if (item[CustomerDataTable.identifier] > result) result = item[CustomerDataTable.identifier]
            }
        }

        return (result + 1).toString()
    }

    fun getNextTicketId(): String {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.select(TicketDataTable.identifier).forEach { item ->
                if (item[TicketDataTable.identifier] > result) result = item[TicketDataTable.identifier]
            }
        }

        return (result + 1).toString()
    }

    fun getNextEmployeeId(): String {
        var result = 0

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.select(EmployeeTable.identifier).forEach { item ->
                if (item[EmployeeTable.identifier] > result) result = item[EmployeeTable.identifier]
            }
        }

        return (result + 1).toString()
    }

    fun updateCustomerName(id: String, data: PersonName) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id.toInt() }) { item ->
                item[firstName] = data.firstName
                item[lastName] = data.lastName
            }
        }
    }

    fun updateCustomerAddress(id: String, data: Address) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id.toInt() }) { item ->
                item[street] = data.street
                item[city] = data.city
                item[state] = data.state
                item[zip] = data.zip
            }
        }
    }

    fun updateCustomerContact(id: String, data: ContactInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id.toInt() }) { item ->
                item[contactInfo] = data
            }
        }
    }

    fun updateCustomerTickets(id: String, data: List<String>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id.toInt() }) { item ->
                item[tickets] = TicketList(data)
            }
        }
    }

    fun updateCustomerNotes(id: String, data: List<Note>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.update(where = { CustomerDataTable.identifier eq id.toInt() }) { item ->
                item[notes] = Notes(data)
            }
        }
    }

    fun addCustomer(customer: CustomerData) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            CustomerDataTable.insert { item ->
                item[identifier] = customer.info.identifier.toInt()
                item[firstName] = customer.info.name.firstName
                item[lastName] = customer.info.name.lastName
                item[street] = customer.info.address.street
                item[city] = customer.info.address.city
                item[state] = customer.info.address.state
                item[zip] = customer.info.address.zip
                item[tickets] = TicketList(customer.tickets)
                item[notes] = Notes(customer.notes)
                item[contactInfo] = customer.info.contactInfo
            }
        }
    }

    fun removeCustomer(id: String) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete item
            CustomerDataTable.deleteWhere { identifier eq id.toInt() }
        }
    }

    fun checkIfCustomerExistsInDb(id: String): Boolean {
        return CustomerDataTable.select(CustomerDataTable.identifier).where { CustomerDataTable.identifier eq id.toInt() }.count() > 0
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
                        identifier = item[CustomerDataTable.identifier].toString(),
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
                    item[identifier] = customer.info.identifier.toInt()
                    item[firstName] = customer.info.name.firstName
                    item[lastName] = customer.info.name.lastName
                    item[street] = customer.info.address.street
                    item[city] = customer.info.address.city
                    item[state] = customer.info.address.state
                    item[zip] = customer.info.address.zip
                    item[tickets] = TicketList(customer.tickets)
                    item[notes] = Notes(customer.notes)
                    item[contactInfo] = customer.info.contactInfo
                }
            }
        }
    }

    fun updateTicketUpdates(id: String, updates: Notes) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id.toInt() }) { item ->
                item[TicketDataTable.updates] = updates
            }
        }
    }

    fun updateTicketStatus(id: String, status: TicketStatus) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id.toInt() }) { item ->
                item[TicketDataTable.status] = status
            }
        }
    }

    fun updateTicketPriority(id: String, priority: TicketPriority) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id.toInt() }) { item ->
                item[TicketDataTable.priority] = priority
            }
        }
    }

    fun updateTicketEmply(id: String, employeeNum: String, employeeName: PersonName) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id.toInt() }) { item ->
                item[TicketDataTable.assignedEmp] = employeeNum
            }
        }
    }

    fun updateTicketDevice(id: String, device: Device) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id.toInt() }) { item ->
                item[TicketDataTable.device] = device
            }
        }
    }

    fun updateTicketCustId(id: String, custId: String) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq id.toInt() }) { item ->
                item[TicketDataTable.customerId] = custId
            }
        }
    }

    fun updateTicket(data: TicketInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.update({ TicketDataTable.identifier eq data.identifier.toInt() }) { item ->
                item[TicketDataTable.identifier] = data.identifier.toInt()
                item[TicketDataTable.customerId] = data.customerId
                item[TicketDataTable.device] = data.device
                item[TicketDataTable.assignedEmp] = data.assignedEmployee
                item[TicketDataTable.priority] = data.priority
                item[TicketDataTable.status] = data.status
                item[TicketDataTable.updates] = Notes(data.updates)
            }
        }
    }

    fun addTicket(data: TicketInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            TicketDataTable.insert { item ->
                item[TicketDataTable.identifier] = data.identifier.toInt()
                item[TicketDataTable.customerId] = data.customerId
                item[TicketDataTable.device] = data.device
                item[TicketDataTable.assignedEmp] = data.assignedEmployee
                item[TicketDataTable.priority] = data.priority
                item[TicketDataTable.status] = data.status
                item[TicketDataTable.updates] = Notes(data.updates)
            }
        }
    }

    fun removeTicket(id: String) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete item
            TicketDataTable.deleteWhere { identifier eq id.toInt() }
        }
    }

    fun addAllTicketData(data: List<TicketInfo>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Batch insert
            TicketDataTable.batchInsert(data) { item ->
                this[TicketDataTable.identifier] = item.identifier.toInt()
                this[TicketDataTable.customerId] = item.customerId
                this[TicketDataTable.device] = item.device
                this[TicketDataTable.assignedEmp] = item.assignedEmployee
                this[TicketDataTable.priority] = item.priority
                this[TicketDataTable.status] = item.status
                this[TicketDataTable.updates] = Notes(item.updates)
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
                    identifier = item[TicketDataTable.identifier].toString(),
                    customerId = item[TicketDataTable.customerId],
                    device = item[TicketDataTable.device],
                    assignedEmployee = item[TicketDataTable.assignedEmp],
                    priority = item[TicketDataTable.priority],
                    status = item[TicketDataTable.status],
                    updates = item[TicketDataTable.updates].notes
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
                item[EmployeeTable.identifier] = emp.identifier.toInt()
                item[EmployeeTable.firstName] = emp.name.firstName
                item[EmployeeTable.lastName] = emp.name.lastName
                item[EmployeeTable.contactInfo] = emp.contactInfo
                item[EmployeeTable.tickets] = TicketList(emp.tickets)
                item[EmployeeTable.notes] = Notes(emp.notes)
                item[EmployeeTable.access] = emp.accessLevel
            }
        }
    }

    fun getEmployee(id: String): Employee {
        var result = Employee()

        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            result = EmployeeTable.selectAll().where { EmployeeTable.identifier eq id.toInt() }.map { value ->
                Employee(
                    identifier = value[EmployeeTable.identifier].toString(),
                    name = PersonName(value[EmployeeTable.firstName], value[EmployeeTable.lastName]),
                    notes = value[EmployeeTable.notes].notes,
                    contactInfo = value[EmployeeTable.contactInfo],
                    tickets = value[EmployeeTable.tickets].list,
                    accessLevel = value[EmployeeTable.access]
                )
            }.singleOrNull() ?: Employee()
        }

        return result
    }

    fun removeEmployee(id: String) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Delete employee
            EmployeeTable.deleteWhere { identifier eq id.toInt() }
        }
    }

    fun addAllEmployees(employees: List<Employee>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            // Batch insert
            EmployeeTable.batchInsert(employees) { item ->
                this[EmployeeTable.identifier] = item.identifier.toInt()
                this[EmployeeTable.firstName] = item.name.firstName
                this[EmployeeTable.lastName] = item.name.lastName
                this[EmployeeTable.contactInfo] = item.contactInfo
                this[EmployeeTable.notes] = Notes(item.notes)
                this[EmployeeTable.tickets] = TicketList(item.tickets)
                this[EmployeeTable.access] = item.accessLevel
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
                        identifier = item[EmployeeTable.identifier].toString(),
                        name = PersonName(item[EmployeeTable.firstName], item[EmployeeTable.lastName]),
                        notes = item[EmployeeTable.notes].notes,
                        contactInfo = item[EmployeeTable.contactInfo],
                        tickets = item[EmployeeTable.tickets].list,
                        accessLevel = item[EmployeeTable.access]
                    )
                )
            }
        }

        return result
    }

    fun updateEmployeeName(id: String, name: PersonName) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id.toInt() }) { item ->
                item[EmployeeTable.firstName] = name.firstName
                item[EmployeeTable.lastName] = name.lastName
            }
        }
    }

    fun updateEmployeeTickets(id: String, tickets: List<String>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id.toInt() }) { item ->
                item[EmployeeTable.tickets] = TicketList(tickets)
            }
        }
    }

    fun updateEmployeeContactInfo(id: String, contact: ContactInfo) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id.toInt() }) { item ->
                item[contactInfo] = contact
            }
        }
    }

    fun updateEmployeeNotes(id: String, note: List<Note>) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id.toInt() }) { item ->
                item[notes] = Notes(note)
            }
        }
    }

    fun updateEmployeeAccess(id: String, accessLevel: AccessLevel) {
        transaction {
            // If we should log, setup logger
            if (shouldLog) addLogger(StdOutSqlLogger)

            EmployeeTable.update({ EmployeeTable.identifier eq id.toInt() }) { item ->
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


fun testSqliteDatabase() {
    Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(Tasks)

        val taskId = Tasks.insert {
            it[title] = "Learn Exposed"
            it[description] = "Go through the Get started with Exposed tutorial"
        } get Tasks.id

        val secondTaskId = Tasks.insert {
            it[title] = "Read The Hobbit"
            it[description] = "Read the first two chapters of The Hobbit"
            it[isCompleted] = true
        } get Tasks.id

        println("Created new tasks with ids $taskId and $secondTaskId.")

        val find = Tasks.select(Tasks.title.locate("Read")).count()

        println("Searched and found: $find")

        // How to find an entry and get all of its columns
        val item = Tasks.selectAll().where { Tasks.title.eq("Read The Hobbit") }.first()

        println("Id: ${item[Tasks.id]}")
        println("Title: ${item[Tasks.title]}")
        println("Description: ${item[Tasks.description]}")
        println("IsComplete: ${item[Tasks.isCompleted]}")

        // This is how you find an entry and only pull specific columns
        Tasks.select(Tasks.id, Tasks.title, Tasks.description, Tasks.isCompleted).where { Tasks.title.eq("Read The Hobbit") }.forEach {
            println("Id: ${it[Tasks.id]}")
            println("Title: ${it[Tasks.title]}")
            println("Description: ${it[Tasks.description]}")
            println("IsComplete: ${it[Tasks.isCompleted]}")
        }

        Tasks.select(Tasks.id.count(), Tasks.isCompleted).groupBy(Tasks.isCompleted).forEach {
            println("${it[Tasks.isCompleted]}: ${it[Tasks.id.count()]} ")
        }

        // Update a task
        Tasks.update({ Tasks.id eq taskId }) {
            it[isCompleted] = true
        }

        val updatedTask = Tasks.select(Tasks.isCompleted).where(Tasks.id eq taskId).single()

        println("Updated task details: $updatedTask")

        // Delete a task
        Tasks.deleteWhere { id eq secondTaskId }

        println("Remaining tasks: ${Tasks.selectAll().toList()}")
    }
}

// Connect to a db
// Database.connect("jdbc:sqlite:/data/data.db", "org.sqlite.JDBC")

// in memory
// Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

// TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
//// or Connection.TRANSACTION_READ_UNCOMMITTED
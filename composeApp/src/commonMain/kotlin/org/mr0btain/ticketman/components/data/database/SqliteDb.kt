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

class SqliteDb() {
    // TODO: Setup error handling
    // TODO: Flesh these out better to support copying syncing and such between dbs
    // TODO: Add encryption
    // TODO: Add removal functions
    // TODO: Make more functions for indexing and searching the db for things
    // TODO: Make functions to update specific items for each thing using id
    private val appDir = FileKit.filesDir.path
    private val db = Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
        //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    fun updateCustomer(data: CustomerData) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exists, if not make them
            checkAndCreateTables()

            CustomerDataTable.update(where = { CustomerDataTable.idNumber eq data.info.id }) { item ->
                item[idNumber] = data.info.id
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

    fun updateCustomerName(id: String, data: CustomerName) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exists, if not make them
            checkAndCreateTables()

            CustomerDataTable.update(where = { CustomerDataTable.idNumber eq id }) { item ->
                item[firstName] = data.firstName
                item[lastName] = data.lastName
            }
        }
    }

    fun updateCustomerAddress(id: String, data: Address) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exists, if not make them
            checkAndCreateTables()

            CustomerDataTable.update(where = { CustomerDataTable.idNumber eq id }) { item ->
                item[street] = data.street
                item[city] = data.city
                item[state] = data.state
                item[zip] = data.zip
            }
        }
    }

    fun updateCustomerContact(id: String, data: ContactInfo) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exists, if not make them
            checkAndCreateTables()

            CustomerDataTable.update(where = { CustomerDataTable.idNumber eq id }) { item ->
                item[contactInfo] = data
            }
        }
    }

    fun updateCustomerTickets(id: String, data: List<String>) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exists, if not make them
            checkAndCreateTables()

            CustomerDataTable.update(where = { CustomerDataTable.idNumber eq id }) { item ->
                item[tickets] = TicketList(data)
            }
        }
    }

    fun updateCustomerNotes(id: String, data: List<Note>) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exists, if not make them
            checkAndCreateTables()

            CustomerDataTable.update(where = { CustomerDataTable.idNumber eq id }) { item ->
                item[notes] = Notes(data)
            }
        }
    }

    fun addCustomer(data: CustomerData) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // check if tables exist if not make them
            checkAndCreateTables()

            CustomerDataTable.insert { item ->
                item[idNumber] = data.info.id
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

    fun getAllCustomerData(): List<CustomerData> {
        val result = mutableListOf<CustomerData>()

        // Go into database and convert data to customer data item
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // Check if the tables exist if not make them
            checkAndCreateTables()

            println("Customer data size: ${CustomerDataTable.selectAll().count()}")

            CustomerDataTable.selectAll().forEach { item ->
                result.add(
                    CustomerData(
                        info = CustomerInfo(
                            id = item[CustomerDataTable.idNumber],
                            name = CustomerName(
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
                )
            }
        }

        return result
    }

    fun addAllCustomerData(data: List<CustomerData>) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // Check if the tables exist if not make them
            checkAndCreateTables()

            data.forEach { customer ->
                CustomerDataTable.insert { item ->
                    item[idNumber] = customer.info.id
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

    fun updateTicket(data: TicketInfo) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // Check if tables exist if not make them
            checkAndCreateTables()

            TicketDataTable.update({ TicketDataTable.idNumber eq data.idNum }) { item ->
                item[TicketDataTable.idNumber] = data.idNum
                item[TicketDataTable.customerId] = data.customerId
                item[TicketDataTable.device] = data.device
                item[TicketDataTable.assignedEmp] = data.assignedEmployee
                item[TicketDataTable.priority] = data.priority
                item[TicketDataTable.status] = data.status
                item[TicketDataTable.notes] = Notes(data.notes)
            }
        }
    }

    fun addTicket(data: TicketInfo) {
        transaction {
            // Setup logger
            addLogger(StdOutSqlLogger)

            // Check if tables exist if not make them
            checkAndCreateTables()

            TicketDataTable.insert { item ->
                item[TicketDataTable.idNumber] = data.idNum
                item[TicketDataTable.customerId] = data.customerId
                item[TicketDataTable.device] = data.device
                item[TicketDataTable.assignedEmp] = data.assignedEmployee
                item[TicketDataTable.priority] = data.priority
                item[TicketDataTable.status] = data.status
                item[TicketDataTable.notes] = Notes(data.notes)
            }
        }
    }

    fun addAllTicketData(data: List<TicketInfo>) {
        transaction {
            // Add logger
            addLogger(StdOutSqlLogger)

            // Check if we need to make the tables
            checkAndCreateTables()

            // Batch insert
            TicketDataTable.batchInsert(data) { item ->
                this[TicketDataTable.idNumber] = item.idNum
                this[TicketDataTable.customerId] = item.customerId
                this[TicketDataTable.device] = item.device
                this[TicketDataTable.assignedEmp] = item.assignedEmployee
                this[TicketDataTable.priority] = item.priority
                this[TicketDataTable.status] = item.status
                this[TicketDataTable.notes] = Notes(item.notes)
            }
        }
    }

    fun getAllTicketData(): List<TicketInfo> {
        val result = mutableListOf<TicketInfo>()

        transaction {
            addLogger(StdOutSqlLogger)

            TicketDataTable.selectAll().forEach { item ->
                result.add(
                    TicketInfo(
                        idNum = item[TicketDataTable.idNumber],
                        customerId = item[TicketDataTable.customerId],
                        device = item[TicketDataTable.device],
                        assignedEmployee = item[TicketDataTable.assignedEmp],
                        priority = item[TicketDataTable.priority],
                        status = item[TicketDataTable.status],
                        notes = item[TicketDataTable.notes].notes
                    )
                )
            }
        }

        return result
    }

    fun checkAndCreateTables() {
        transaction {
            addLogger(StdOutSqlLogger)

            if (!SchemaUtils.listTables().containsAll(listOf("customer", "tickets"))) {
                SchemaUtils.create(CustomerDataTable, TicketDataTable)
            }
        }
    }

    fun listTables(): List<String> {
        var result = listOf<String>()

        transaction { result = SchemaUtils.listTables() }

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
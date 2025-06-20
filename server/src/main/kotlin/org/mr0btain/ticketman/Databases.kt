package org.mr0btain.ticketman

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.Priority
import org.mr0btain.ticketman.components.Task
import org.mr0btain.ticketman.components.TaskRepository
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.employee.Employee
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.LocalAppDatabase
import org.mr0btain.ticketman.server.ServerResponse
import org.mr0btain.ticketman.server.ServerResult
import java.sql.Connection
import java.sql.DriverManager

// TODO: Add sync functionality to sync local and remote databases

fun Application.configureDatabases(
    repository: TaskRepository,
    localAppDatabase: LocalAppDatabase
) {
    install(ContentNegotiation) { json() }

    routing {
        route("/customers") {
            // Get all customers
            get("/all") { call.respond(localAppDatabase.getAllCustomers()) }

            // Adding new customer
            post("/add") {
                try {
                    val customer = call.receive<CustomerData>()
                    localAppDatabase.addCustomer(customer)
                    call.respond(HttpStatusCode.NoContent)
                } catch (ex: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest)
                } catch (ex: JsonConvertException) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }

            // Update Customer
            post("/update") {
                try {
                    val customer = call.receive<CustomerData>()

                    if (localAppDatabase.updateCustomer(customer)) {
                        call.respond(HttpStatusCode.Accepted)
                    } else call.respond(HttpStatusCode.BadRequest)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(HttpStatusCode.BadRequest)
                }
            }

            // Bulk add customers
            post("/add/all") {
                try {
                    val customers = call.receive<List<CustomerData>>()

                    if (localAppDatabase.addAllCustomers(customers)) {
                        call.respond(ServerResponse(ServerResult.SUCCESS))
                    } else call.respond(ServerResponse(ServerResult.FAIL))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Delete customer
            delete("/delete/{customerIdentifier}") {
                val identifier = call.parameters["customerIdentifier"]?.toIntOrNull()

                if (identifier == null) {
                    call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                    return@delete
                }

                if (localAppDatabase.removeCustomer(identifier)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else {
                    call.respond(ServerResponse(ServerResult.FAIL))
                }
            }
        }

        route("/tickets") {
            // Get all tickets
            get("/all") { call.respond(localAppDatabase.getAllTickets()) }

            // Add New Ticket
            post("/add") {
                try {
                    val ticket = call.receive<TicketInfo>()
                    localAppDatabase.addTicket(ticket)
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Update ticket
            post("/update") {
                try {
                    val ticket = call.receive<TicketInfo>()
                    localAppDatabase.updateTicket(ticket)
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Bulk add tickets
            post("/add/all") {
                try {
                    val tickets = call.receive<List<TicketInfo>>()

                    if (localAppDatabase.addAllTickets(tickets)) {
                        call.respond(ServerResponse(ServerResult.SUCCESS))
                    } else call.respond(ServerResponse(ServerResult.FAIL))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Delete ticket
            delete("/delete/{ticketIdentifier}") {
                val identifier = call.parameters["ticketIdentifier"]?.toIntOrNull()

                if (identifier == null) {
                    call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                    return@delete
                }

                if (localAppDatabase.removeTicket(identifier)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else {
                    call.respond(ServerResponse(ServerResult.FAIL))
                }
            }
        }

        route("/employees") {
            // Get all employees
            get("/all") { call.respond(localAppDatabase.getAllEmployees()) }

            // Add New employee
            post("/add") {
                try {
                    val employee = call.receive<Employee>()
                    localAppDatabase.addEmployee(employee)
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Update employee
            post("/update") {
                try {
                    val employee = call.receive<Employee>()
                    localAppDatabase.updateEmployee(employee)
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Bulk add employees
            post("/add/all") {
                try {
                    val employees = call.receive<List<Employee>>()

                    if (localAppDatabase.addAllEmployees(employees)) {
                        call.respond(ServerResponse(ServerResult.SUCCESS))
                    } else call.respond(ServerResponse(ServerResult.FAIL))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Delete employee
            delete("/delete/{employeeIdentifier}") {
                val identifier = call.parameters["employeeIdentifier"]?.toIntOrNull()

                if (identifier == null) {
                    call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                    return@delete
                }

                if (localAppDatabase.removeTicket(identifier)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else {
                    call.respond(ServerResponse(ServerResult.FAIL))
                }
            }
        }

        route("/inventory") {
            // Get all inventory
            get("/all") { call.respond(localAppDatabase.getInventory()) }

            // Add New item
            post("/add") {
                try {
                    val inventoryItem = call.receive<InventoryItem>()
                    localAppDatabase.addInventoryItem(inventoryItem)
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Update item
            post("/update") {
                try {
                    val inventoryItem = call.receive<InventoryItem>()
                    localAppDatabase.updateInventoryItem(inventoryItem)
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Bulk add employees
            post("/add/all") {
                try {
                    val inventoryItems = call.receive<List<InventoryItem>>()

                    if (localAppDatabase.addAllInventory(inventoryItems)) {
                        call.respond(ServerResponse(ServerResult.SUCCESS))
                    } else call.respond(ServerResponse(ServerResult.FAIL))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
                }
            }

            // Delete item
            delete("/delete/{inventoryIdentifier}") {
                val identifier = call.parameters["inventoryIdentifier"]?.toIntOrNull()

                if (identifier == null) {
                    call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                    return@delete
                }

                if (localAppDatabase.removeInventoryItem(identifier)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else {
                    call.respond(ServerResponse(ServerResult.FAIL))
                }
            }
        }

        route("/tasks") {
            get {
                val tasks = repository.allTasks()
                call.respond(tasks)
            }

            get("/byName/{taskName}") {
                val name = call.parameters["taskName"]
                if (name == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val task = repository.taskByName(name)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(task)
            }

            get("/byPriority/{priority}") {
                val priorityAsText = call.parameters["priority"]
                if (priorityAsText == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                try {
                    val priority = Priority.valueOf(priorityAsText)
                    val tasks = repository.tasksByPriority(priority)


                    if (tasks.isEmpty()) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respond(tasks)
                } catch (ex: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }

            post {
                try {
                    val task = call.receive<Task>()
                    repository.addTask(task)
                    call.respond(HttpStatusCode.NoContent)
                } catch (ex: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest)
                } catch (ex: JsonConvertException) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }

            delete("/{taskName}") {
                val name = call.parameters["taskName"]
                if (name == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                if (repository.removeTask(name)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

/**
 * Makes a connection to a Postgres database.
 *
 * In order to connect to your running Postgres process,
 * please specify the following parameters in your configuration file:
 * - postgres.url -- Url of your running database process.
 * - postgres.user -- Username for database connection
 * - postgres.password -- Password for database connection
 *
 * If you don't have a database process running yet, you may need to [download]((https://www.postgresql.org/download/))
 * and install Postgres and follow the instructions [here](https://postgresapp.com/).
 * Then, you would be able to edit your url,  which is usually "jdbc:postgresql://host:port/database", as well as
 * user and password values.
 *
 *
 * @param embedded -- if [true] defaults to an embedded database for tests that runs locally in the same process.
 * In this case you don't have to provide any parameters in configuration file, and you don't have to run a process.
 *
 * @return [Connection] that represent connection to the database. Please, don't forget to close this connection when
 * your application shuts down by calling [Connection.close]
 * */

fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    if (embedded) {
        log.info("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val url = environment.config.property("postgres.url").getString()
        log.info("Connecting to postgres database at $url")
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        return DriverManager.getConnection(url, user, password)
    }
}

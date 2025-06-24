package org.mr0btain.ticketman

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.data.employee.Employee
import org.mr0btain.ticketman.database.LocalDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import java.sql.Connection
import java.sql.DriverManager

// TODO: Add sync functionality to sync local and remote databases

fun Application.configureDatabases(
    localDatabase: LocalDatabase
) {
    install(ContentNegotiation) { json() }

    routing {
        route("/employees") {
            // Get all employees
            get("/all") { call.respond(localDatabase.getAllEmployees()) }

            // Add New employee
            post("/add") {
                try {
                    val employee = call.receive<Employee>()
                    localDatabase.addEmployee(employee)
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
                    localDatabase.updateEmployee(employee)
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

                    if (localDatabase.addAllEmployees(employees)) {
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

                if (localDatabase.removeTicket(identifier)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else {
                    call.respond(ServerResponse(ServerResult.FAIL))
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

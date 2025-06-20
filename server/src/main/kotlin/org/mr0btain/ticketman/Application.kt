package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.FakeTaskRepository
import org.mr0btain.ticketman.database.LocalAppDatabase

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val repository = FakeTaskRepository()
    val local = LocalAppDatabase("C:/Users/wnbar/Documents/ServerDatabase")

    // Initialize our database here for now
    local.checkAndCreateTables()
    /*
    val testData = generateMockData(60)

    local.addAllCustomers(testData.first)
    local.addAllTickets(testData.second)
    local.addAllEmployees(testData.third)
     */

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }

    configureSerialization()
    configureDatabases(repository, local)
    configureRouting()
}
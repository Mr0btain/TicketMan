package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.FakeTaskRepository
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.repository.UserRepository
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.database.LocalAppDatabase

/*
fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
 */

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val repository = FakeTaskRepository()
    val local = LocalAppDatabase("C:/Users/wnbar/Documents/ServerDatabase")
    val userRepository = UserRepository()
    val userService = UserService(userRepository)
    val jwtService = JwtService(this, userService)

    // Initialize our database here for now
    local.checkAndCreateTables()

    /*
    val testData = generateMockData(60)

    local.addAllCustomers(testData.first)
    local.addAllTickets(testData.second)
    local.addAllEmployees(testData.third)
     */
    //local.addAllInventory(generateRandomInventoryItems(50))

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }

    configureSerialization()
    configureDatabases(repository, local)
    configureRouting(userService, jwtService)
    configureSecurity(jwtService)
}
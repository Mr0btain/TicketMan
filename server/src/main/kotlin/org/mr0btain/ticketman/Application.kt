package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.repository.UserRepository
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService

// TODO: Persist user data
// TODO: Add auth to routes for database actions
// TODO: Setup way for admin to change access levels for things individually

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val database = configureDatabase()
    val userRepository = UserRepository()

    val jwtService = JwtService(this, database.userDatabase)
    val userService = UserService(database.userDatabase, jwtService, database.tokenDatabase)

    configureSerialization()
    configureContentNegotiation()
    configureSecurity(jwtService)
    configureRouting(userService, database)


    /*
    val testData = generateMockData(10)

    localAppDatabase.addAllCustomers(testData.first)
    localAppDatabase.addAllTickets(testData.second)
    localAppDatabase.addAllEmployees(testData.third)
    localAppDatabase.addAllInventory(generateRandomInventoryItems(10))
     */

}
package org.mr0btain.ticketman

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.repository.RefreshTokenRepository
import org.mr0btain.ticketman.components.repository.UserRepository
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.database.LocalAppDatabase

// TODO: Persist user data
// TODO: Add auth to routes for database actions

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    //val local = LocalAppDatabase("C:/Users/wnbar/Documents/ServerDatabase")
    val localAppDatabase = LocalAppDatabase("/home/nick/.local/share/TicketMan")
    val userRepository = UserRepository()
    val refreshTokenRepository = RefreshTokenRepository()

    val jwtService = JwtService(this, userRepository)
    val userService = UserService(userRepository, jwtService, refreshTokenRepository)

    configureSerialization()
    configureContentNegotiation()
    configureSecurity(jwtService)
    configureRouting(userService, localAppDatabase)

    // Initialize our database here for now
    localAppDatabase.checkAndCreateTables()


    /*
    val testData = generateMockData(10)

    localAppDatabase.addAllCustomers(testData.first)
    localAppDatabase.addAllTickets(testData.second)
    localAppDatabase.addAllEmployees(testData.third)
    localAppDatabase.addAllInventory(generateRandomInventoryItems(10))
     */

}
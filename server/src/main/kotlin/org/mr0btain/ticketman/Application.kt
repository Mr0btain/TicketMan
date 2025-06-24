package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.repository.RefreshTokenRepository
import org.mr0btain.ticketman.components.repository.UserRepository
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.database.LocalDatabase

// TODO: Persist user data
// TODO: Add auth to routes for database actions
// TODO: Setup way for admin to change access levels for things individually

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    //val local = LocalAppDatabase("C:/Users/wnbar/Documents/ServerDatabase")
    val localDatabase = LocalDatabase("/home/nick/.local/share/TicketMan")
    val userRepository = UserRepository()
    val refreshTokenRepository = RefreshTokenRepository()

    val jwtService = JwtService(this, userRepository)
    val userService = UserService(userRepository, jwtService, refreshTokenRepository)

    configureSerialization()
    configureContentNegotiation()
    configureSecurity(jwtService)
    configureRouting(userService, localDatabase)

    // Initialize our database here for now
    localDatabase.checkAndCreateTables()


    /*
    val testData = generateMockData(10)

    localAppDatabase.addAllCustomers(testData.first)
    localAppDatabase.addAllTickets(testData.second)
    localAppDatabase.addAllEmployees(testData.third)
    localAppDatabase.addAllInventory(generateRandomInventoryItems(10))
     */

}
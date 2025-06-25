package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.mr0btain.ticketman.components.database.configureDatabase
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
    val database = configureDatabase("/home/nick/.local/share/TicketMan", true)
    val userRepository = UserRepository()
    val refreshTokenRepository = RefreshTokenRepository()

    val jwtService = JwtService(this, userRepository)
    val userService = UserService(userRepository, jwtService, refreshTokenRepository)

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
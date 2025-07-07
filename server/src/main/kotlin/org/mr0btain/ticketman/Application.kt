package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.v1.core.exposedLogger
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.encryption.testSecureClient
import org.mr0btain.ticketman.encryption.testSecureClient2
import org.mr0btain.ticketman.util.AppDir
import java.util.logging.Logger

// TODO: Add auth to routes for database actions
// TODO: Setup way for admin to change access levels for things individually
// TODO: Maybe add ability for different users to have different instances of the databases and add admin settings to force all sync or allow individual

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val appDir = AppDir()

    // TODO: Eventually we will switch this over to use an environment variable or the directory the app is running in
    // as this is typical for servers. For now i need it to be in a specific place on my laptops so were using this
    // Check path dir
    if (appDir.path != null) {
        val database = configureDatabase(appDir.path.toString())
        val jwtService = JwtService(this, database.userDatabase)
        val userService = UserService(database.userDatabase, jwtService, database.tokenDatabase)

        println("USER: ${database.userDatabase.getAll()?.firstOrNull { it.accessLevel == AccessLevel.ADMIN }}")

        configureSerialization()
        configureContentNegotiation()
        configureSecurity(jwtService)
        configureRouting(userService, database)

        testSecureClient()

        testSecureClient2()

        /*

val testData = generateMockData(20)
        database.customerDatabase.addAll(testData.first)
        database.ticketDatabase.addAll(testData.second)
        database.userDatabase.addAll(testData.third)
        database.inventoryDatabase.addAll(generateRandomInventoryItems(60))
         */
    }
}
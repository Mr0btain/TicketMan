package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.util.AppDir

// TODO: Setup way for admin to change access levels for things individually
// TODO: Maybe add ability for different users to have different instances of the databases and add admin settings to force all sync or allow individual

/**
 * Main
 * Assume all requests are through http, encrypt everything.
 */

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val path = this.environment.config.property("env.dataPath").getString()
    val appDir = AppDir().path

    println("Path: $path")
    println("AppDir: $appDir")

    // TODO: Eventually we will switch this over to use an environment variable or the directory the app is running in
    // as this is typical for servers. For now i need it to be in a specific place on my laptops so were using this
    // Check path dir
    if (path.isNotBlank() || appDir != null) {
        val database = configureDatabase(path.ifBlank { appDir.toString() })
        val jwtService = JwtService(this, database.userDatabase)
        val userService = UserService(database.userDatabase, jwtService, database.tokenDatabase)
        val encryptionService = EncryptionService()

        println("USER: ${database.userDatabase.getAll()?.firstOrNull { it.accessLevel == AccessLevel.ADMIN }}")

        configureSerialization()
        configureContentNegotiation()
        configureSecurity(jwtService)
        configureRouting(userService, database, encryptionService)

        /*
        val testData = generateMockData(20)
        database.customerDatabase.addAll(testData.first)
        database.ticketDatabase.addAll(testData.second)
        database.userDatabase.addAll(testData.third)
        database.inventoryDatabase.addAll(generateRandomInventoryItems(60))
         */
    }
}
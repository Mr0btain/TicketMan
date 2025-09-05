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
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.util.AppDir

// TODO: Setup way for admin to change access levels for things individually
// TODO: Maybe add ability for different users to have different instances of the databases and add admin settings to force all sync or allow individual

// Currently im thinking we first pass keys, then we log in, then on approval we assign the user a random identifier and refresh the encryption key
// that we store for that specific user. So when we talk first, its un-encrypted. then once were encrypted and user logs in we respond with the unique identifier
// that's used from then on. so it would be ever-changing between sessions.

// TODO: Should also setup some sort of timeout for session encryption and tokens. So after so much time we clear them?

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

    println("AppDir: $appDir")

    // TODO: Eventually we will switch this over to use an environment variable or the directory the app is running in

    // Check path dir
    if (path.isNotBlank() || appDir != null) {
        val database = configureDatabase(path.ifBlank { appDir.toString() })
        val jwtService = JwtService(this, database.userDatabase)
        val userService = UserService(database.userDatabase, jwtService, database.tokenDatabase)
        val encryptionService = EncryptionService()

        println("USER: ${database.userDatabase.all()?.firstOrNull { it.accessLevel == AccessLevel.ADMIN }}")

        configureSerialization()
        configureContentNegotiation()
        configureSecurity(jwtService)
        configureRouting(userService, database, encryptionService)

        AppLogger.m("SERVER", "Server initialized!")

        /*
        val testData = generateMockData(20)
        database.customerDatabase.addAll(testData.first)
        database.ticketDatabase.addAll(testData.second)
        database.userDatabase.addAll(testData.third)
        database.inventoryDatabase.addAll(generateRandomInventoryItems(60))
         */
    }
}
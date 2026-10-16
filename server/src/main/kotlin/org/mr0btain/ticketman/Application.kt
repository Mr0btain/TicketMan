package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.util.AppDir
import kotlin.random.Random
import kotlin.uuid.Uuid

// TODO: Setup way for admin to change access levels for things individually
// TODO: Should also setup some sort of timeout for session tokens. So after so much time we clear them?

/**
 * Main
 */

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val path = this.environment.config.property("env.dataPath").getString()
    val appDir = AppDir().path

    println("AppDir: $appDir")

    // Check path dir
    if (path.isNotBlank() || appDir != null) {
        val database = configureDatabase(path.ifBlank { appDir.toString() })
        val jwtService = JwtService(this, database.userDatabase)
        val userService = UserService(database.userDatabase, jwtService, database.tokenDatabase)

        configureSerialization()
        configureContentNegotiation()
        configureSecurity(jwtService)
        configureRouting(userService, database)

        AppLogger.m("SERVER", "Server initialized!")


        val generateTestData = false

        val admin = database.userDatabase.getAdmin()

        println("Admin: $admin")

        if (generateTestData) {
            val ticketIdentifiers = mutableListOf<Uuid>()
            val ticketNum = 1000
            val customerNum = 400
            val inventoryNum = 100
            val userNum = 25

            (0..ticketNum).forEach { i ->
                // Generate random ticket
                val (ticket, notes) = generateRandomTicket()

                // Add ticket to database
                database.ticketDatabase.add(ticket)

                // Add notes
                notes.forEach { note ->
                    database.ticketDatabase.addNote(ticket.identifier, note)
                }

                // Insert id into list
                ticketIdentifiers.add(ticket.identifier)
            }

            val customerTickets = ticketIdentifiers

            (0..customerNum).forEach { _ ->
                // Generate random customer
                val (customer, notes) = generateMockCustomer()

                // Add customer
                database.customerDatabase.add(customer)

                // Add notes
                notes.forEach { note ->
                    database.customerDatabase.addNote(customer.identifier, note)
                }

                // Add random tickets
                (0..Random.nextInt(1, 6)).forEach { _ ->
                    if (customerTickets.isNotEmpty())
                        database.customerDatabase.addTicket(customer.identifier, customerTickets.removeFirst())
                }
            }

            (0..inventoryNum).forEach { _ ->
                val (inventory, notes) = generateRandomInventoryItem()

                //println(inventory)

                database.inventoryDatabase.add(inventory)

                notes.forEach { note ->
                    database.inventoryDatabase.addNote(inventory.identifier, note)
                }
            }

            val userTickets = ticketIdentifiers

            (0..userNum).forEach { _ ->
                // Generate random user
                val (user, notes) = generateMockUser()

                // Add user to database
                database.userDatabase.add(user)

                //Add notes
                notes.forEach { note ->
                    database.userDatabase.addNote(user.identifier, note)
                }

                // Add random tickets
                (0..Random.nextInt(4, 12)).forEach { _ ->
                    if (userTickets.isNotEmpty())
                        database.userDatabase.addTicket(user.identifier, userTickets.removeFirst())
                }
            }
        }
    }
}
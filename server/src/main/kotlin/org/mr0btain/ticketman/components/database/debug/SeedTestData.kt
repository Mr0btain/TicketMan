package org.mr0btain.ticketman.components.database.debug

import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.server.request.SeedRequest
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * Fills the database with mock tickets, customers, inventory and users. Tickets get handed out to customers
 * and then users until they run out.
 */
fun seedTestData(database: LocalDatabase, request: SeedRequest) {
    val ticketIdentifiers = mutableListOf<Uuid>()

    repeat(request.tickets) {
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

    repeat(request.customers) {
        // Generate random customer
        val (customer, notes) = generateMockCustomer()

        // Add customer
        database.customerDatabase.add(customer)

        // Add notes
        notes.forEach { note ->
            database.customerDatabase.addNote(customer.identifier, note)
        }

        // Add random tickets
        repeat(Random.nextInt(1, 6)) {
            if (ticketIdentifiers.isNotEmpty())
                database.customerDatabase.addTicket(customer.identifier, ticketIdentifiers.removeFirst())
        }
    }

    repeat(request.inventory) {
        val (inventory, notes) = generateRandomInventoryItem()

        database.inventoryDatabase.add(inventory)

        notes.forEach { note ->
            database.inventoryDatabase.addNote(inventory.identifier, note)
        }
    }

    repeat(request.users) {
        // Generate random user
        val (user, notes) = generateMockUser()

        // Mock names repeat, skip ones that would clash on username
        if (database.userDatabase.getByUsername(user.username) != null) return@repeat

        // Add user to database
        database.userDatabase.add(user)

        //Add notes
        notes.forEach { note ->
            database.userDatabase.addNote(user.identifier, note)
        }

        // Add random tickets
        repeat(Random.nextInt(4, 12)) {
            if (ticketIdentifiers.isNotEmpty())
                database.userDatabase.addTicket(user.identifier, ticketIdentifiers.removeFirst())
        }
    }
}

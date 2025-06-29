package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.database.tables.CustomerDataTable
import org.mr0btain.ticketman.database.tables.InventoryTable
import org.mr0btain.ticketman.database.tables.TicketDataTable
import org.mr0btain.ticketman.database.tables.UserTable

// TODO: Flesh these out better to support copying syncing and such between dbs
// TODO: Add encryption
// TODO: Make more functions for indexing and searching the db for things
// TODO: Find better db for android. really would like to use SQLite. Maybe make expect actual
// TODO: Setup data encryption for sensitive info
// TODO: Add functions and make databases private?

/**
 * Handles/holds local databases
 */
class DatabaseHandler(
    appDir: String,
    shouldLog: Boolean = true
) {
    // Connect to the local database
    private val db = Database.connect("jdbc:h2:$appDir/database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    //Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
    // Database.connect("jdbc:h2:$appDir/testData.db", "org.h2.Driver")
    //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    val customerDatabase = LocalDatabaseClient<CustomerData>(CustomerDataTable, shouldLog)
    val ticketDatabase = LocalDatabaseClient<TicketInfo>(TicketDataTable, shouldLog)
    val inventoryDatabase = LocalDatabaseClient<InventoryItem>(InventoryTable, shouldLog)
    val userDatabase = LocalDatabaseClient<User>(UserTable, shouldLog)
}
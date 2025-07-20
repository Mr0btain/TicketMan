package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.jdbc.Database

// TODO: Flesh these out better to support copying syncing and such between dbs
// TODO: Add encryption
// TODO: Make more functions for indexing and searching the db for things
// TODO: Find better db for android. really would like to use SQLite. Maybe make expect actual
// TODO: Setup data encryption for sensitive info
// TODO: Add functions and make databases private?

/**
 * Handles/holds local databases
 */
abstract class DatabaseHandler(
    appDir: String,
    shouldLog: Boolean = true
) {
    companion object {
        const val TAG = "DatabaseHandler"
    }

    // Connect to the local database
    protected val db = Database.connect("jdbc:h2:$appDir/database/database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    //Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
    // Database.connect("jdbc:h2:$appDir/testData.db", "org.h2.Driver")
    //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    val customerDatabase = CustomerLocalDatabaseClientImpl(shouldLog)
    val ticketDatabase = TicketLocalDatabaseClientImpl(shouldLog)
    val inventoryDatabase = InventoryLocalDatabaseClientImpl(shouldLog)
    val userDatabase = UserLocalDatabaseClientImpl(shouldLog)
}
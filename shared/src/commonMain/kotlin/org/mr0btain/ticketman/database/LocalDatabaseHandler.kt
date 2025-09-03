package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Handles/holds local databases
 */
abstract class LocalDatabaseHandler(
    appDir: String,
    shouldLog: Boolean = false
) {
    companion object {
        const val TAG = "DatabaseHandler"
    }

    // Connect to the local database
    protected val db = Database.connect("jdbc:h2:$appDir/database/database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    //Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
    // Database.connect("jdbc:h2:$appDir/testData.db", "org.h2.Driver")
    //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    val customerDatabase = CustomerLocalDatabaseImpl(shouldLog = shouldLog)
    val ticketDatabase = TicketLocalDatabaseImpl(shouldLog = shouldLog)
    val inventoryDatabase = InventoryLocalDatabaseImpl(shouldLog = shouldLog)
    val userDatabase = UserLocalDatabaseImpl(shouldLog = shouldLog)
}
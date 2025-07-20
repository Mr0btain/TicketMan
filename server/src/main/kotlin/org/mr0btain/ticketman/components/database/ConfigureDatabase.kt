package org.mr0btain.ticketman.components.database

/**
 * Initializes the server database
 */
// TODO: Get this to work with OS file dir
fun configureDatabase(
    databaseDirectory: String
): ServerDatabase {
    return ServerDatabase("$databaseDirectory/server").also { database ->
        /** Anything you want to run before using the database do it here */
        database.initDatabase()
    }
}
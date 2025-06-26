package org.mr0btain.ticketman.components.database

/**
 * Configures and returns a ServerDatabase
 */
fun configureDatabase(
    appDir: String,
    shouldLog: Boolean = true
): ServerDatabase {
    // Initialize database
    val database = ServerDatabase(appDir, shouldLog)

    // Setup tables
    database.initDatabase()

    return database
}
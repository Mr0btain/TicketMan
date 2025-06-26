package org.mr0btain.ticketman.components.database

/**
 * Configures and returns a ServerDatabase
 */
fun configureDatabase(
): ServerDatabase {
    // Initialize database
    val database = ServerDatabase("/home/nick/.local/share/TicketMan/data/server", true)

    // Setup tables
    database.initDatabase()

    return database
}
package org.mr0btain.ticketman.components.database

/**
 * Configures and returns a ServerDatabase
 */
// TODO: Get this to work with OS
fun configureDatabase(
): ServerDatabase {
    // Initialize database
    val database = ServerDatabase("/home/nick/.local/share/TicketMan/data/server", true)

    // Setup tables
    database.initDatabase()

    return database
}
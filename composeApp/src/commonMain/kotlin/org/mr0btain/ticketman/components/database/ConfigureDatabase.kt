package org.mr0btain.ticketman.components.database

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path

fun configureDatabase(): ClientDatabase {
    val database = ClientDatabase(FileKit.filesDir.path)

    // Create tables if they don't exist
    database.createTables()

    return database
}
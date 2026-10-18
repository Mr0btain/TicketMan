package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.components.service.RestoreService
import java.io.File

/**
 * Initializes the server database
 */
// TODO: Get this to work with OS file dir
fun configureDatabase(
    databaseDirectory: String
): LocalDatabase {
    // A backup uploaded to restore from is swapped in here, before H2 has the files open
    RestoreService.applyPending(File("$databaseDirectory/server"))

    return LocalDatabase("$databaseDirectory/server").also { database ->
        /** Anything you want to run before using the database do it here */
        database.initDatabase()
    }
}
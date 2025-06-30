package org.mr0btain.ticketman.components.database

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path

/**
 * Initialises the client database
 */

fun configureDatabase(): ClientDatabase {
    return ClientDatabase("${FileKit.filesDir.path}/data").also { database ->
        /** Anything you want to run before using the database do it here */
        database.initDatabase()
    }
}
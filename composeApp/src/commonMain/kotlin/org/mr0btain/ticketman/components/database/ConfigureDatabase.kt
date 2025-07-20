package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.util.AppDir


/**
 * Initialises the client database
 */

fun configureDatabase(appDir: AppDir): ClientDatabase {
    return ClientDatabase("${appDir.path}/client").also { database ->
        /** Anything you want to run before using the database do it here */
        database.initDatabase()
    }
}
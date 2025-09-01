package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.database.LocalDatabaseHandler
import org.mr0btain.ticketman.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.database.tables.user.UserTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Basically a holder for local database handlers
 */

class ClientDatabase(
    appDir: String,
    shouldLog: Boolean = true
): LocalDatabaseHandler(appDir, shouldLog) {
    val settingsDatabase = SettingsDatabase(shouldLog)

    /**
     * Initialize the database in case it were dealing with a new install or something happened to database
     */
    fun initDatabase() {
        try {
            transaction {
                val databaseList = SchemaUtils.listDatabases()

                if (databaseList.isNotEmpty()) {
                    if (SchemaUtils.listTables().size != 5) {
                        SchemaUtils.create(CustomerTable, TicketTable, InventoryTable, UserTable, SettingsTable)
                    }
                } else {
                    SchemaUtils.create(CustomerTable, TicketTable, InventoryTable, UserTable, SettingsTable)
                }
            }

            settingsDatabase.initialize()

            AppLogger.m("ClientDatabase: Initialize", SimpleError.SUCCESS)
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i("$TAG: Initialize", SimpleError.SUCCESS)
        }
    }
}
package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.database.DatabaseHandler
import org.mr0btain.ticketman.database.tables.CustomerDataTable
import org.mr0btain.ticketman.database.tables.InventoryTable
import org.mr0btain.ticketman.database.tables.TicketDataTable
import org.mr0btain.ticketman.database.tables.UserTable

class ServerDatabase(
    appDir: String,
    shouldLog: Boolean = true
): DatabaseHandler(appDir, shouldLog) {
    val tokenDatabase = TokenDatabase(shouldLog)

    /**
     * Creates tables if they don't exist
     */
    fun initDatabase() {
        transaction {
            val databaseList = SchemaUtils.listDatabases()

            if (databaseList.isNotEmpty()) {
                if (SchemaUtils.listTables().size != 4) {
                    SchemaUtils.create(CustomerDataTable, TicketDataTable, InventoryTable, UserTable, TokenTable)
                }
            } else {
                SchemaUtils.create(CustomerDataTable, TicketDataTable, InventoryTable, UserTable, TokenTable)
            }
        }
    }
}
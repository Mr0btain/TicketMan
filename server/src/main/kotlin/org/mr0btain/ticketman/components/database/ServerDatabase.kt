package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.database.LocalDatabaseHandler
import org.mr0btain.ticketman.database.tables.customer.CustomerNoteTable
import org.mr0btain.ticketman.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.database.tables.image.ImageTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryNoteTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.ticket.TicketNoteTable
import org.mr0btain.ticketman.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.database.tables.user.UserTable
import org.mr0btain.ticketman.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

class ServerDatabase(
    appDir: String,
    shouldLog: Boolean = true
): LocalDatabaseHandler(appDir, shouldLog) {
    val tokenDatabase = TokenDatabase(shouldLog)

    private val TAG = "ServerDatabase"
    private val INIT_TAG = "$TAG:Init"

    /**
     * Creates tables if they don't exist essentially. Idk if we even need this if we could just straight call creat everytime. ohh well.
     */
    fun initDatabase() {
        transaction {
            val databaseList = SchemaUtils.listDatabases()

            if (databaseList.isEmpty() || databaseList.size != 12) {
                SchemaUtils.create(
                    CustomerTable,
                    CustomerNoteTable,
                    CustomerTicketTable,
                    TicketTable,
                    TicketNoteTable,
                    InventoryTable,
                    InventoryNoteTable,
                    UserTable,
                    UserNoteTable,
                    UserTicketTable,
                    ImageTable,
                    TokenTable
                )
            }

            AppLogger.m(INIT_TAG, SimpleError.SUCCESS)
        }
    }
}
package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.database.TicketDatabaseImpl
import org.mr0btain.ticketman.database.UserDatabaseImpl
import org.mr0btain.ticketman.database.tables.customer.CustomerNoteTable
import org.mr0btain.ticketman.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.database.tables.image.ImageTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryImageTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryNoteTable
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.note.NoteImageTable
import org.mr0btain.ticketman.database.tables.note.NoteTable
import org.mr0btain.ticketman.database.tables.ticket.TicketNoteTable
import org.mr0btain.ticketman.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.database.tables.user.UserTable
import org.mr0btain.ticketman.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger

class LocalDatabase (
    appDir: String
) {
    private val TAG = "ServerDatabase"
    private val INIT_TAG = "$TAG:Init"

    // Connect to the local database. Might not need this as a value
    private val db = Database.connect("jdbc:h2:$appDir/database/database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    //Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
    // Database.connect("jdbc:h2:$appDir/testData.db", "org.h2.Driver")
    //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    val customerDatabase = CustomerDatabaseImpl()
    val ticketDatabase = TicketDatabaseImpl()
    val tokenDatabase = TokenDatabase()
    val inventoryDatabase = InventoryDatabaseImpl()
    val userDatabase = UserDatabaseImpl()

    /**
     * Creates tables if they don't exist essentially. Idk if we even need this if we could just straight call creat everytime. ohh well.
     */
    fun initDatabase() {
        transaction {
            val databaseList = SchemaUtils.listDatabases()

            if (databaseList.isEmpty() || databaseList.size != 14) {
                SchemaUtils.create(
                    CustomerTable,
                    CustomerNoteTable,
                    CustomerTicketTable,
                    TicketTable,
                    TicketNoteTable,
                    InventoryTable,
                    InventoryNoteTable,
                    InventoryImageTable,
                    UserTable,
                    UserNoteTable,
                    UserTicketTable,
                    NoteTable,
                    NoteImageTable,
                    ImageTable,
                    TokenTable
                )
            }

            AppLogger.m(INIT_TAG, SimpleResult.SUCCESS)
        }
    }
}
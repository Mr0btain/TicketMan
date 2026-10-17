package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.audit.AuditLogTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerNoteTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.components.database.tables.image.ImageTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryImageTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryNoteTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.components.database.tables.note.NoteImageTable
import org.mr0btain.ticketman.components.database.tables.note.NoteTable
import org.mr0btain.ticketman.components.database.tables.shop.ShopTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketNoteTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketStatusHistoryTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.io.File

class LocalDatabase (
    appDir: String
) {
    private val TAG = "ServerDatabase"
    private val INIT_TAG = "$TAG:Init"
    private val BACKUP_TAG = "$TAG:Backup"

    /** Folder holding the H2 files */
    val databaseDirectory = File("$appDir/database")

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
    val auditLogDatabase = AuditLogDatabase(userDatabase)
    val shopDatabase = ShopDatabase()
    val statsDatabase = StatsDatabase()
    val counterDatabase = CounterDatabase(ticketDatabase, customerDatabase)

    // Every table, in an order where references come before the tables that use them
    private val tables = arrayOf(
        CustomerTable,
        CustomerNoteTable,
        CustomerTicketTable,
        TicketTable,
        TicketNoteTable,
        TicketStatusHistoryTable,
        InventoryTable,
        InventoryNoteTable,
        InventoryImageTable,
        UserTable,
        UserNoteTable,
        UserTicketTable,
        NoteTable,
        NoteImageTable,
        ImageTable,
        TokenTable,
        ShopTable,
        AuditLogTable
    )

    /**
     * Tables that have gained columns since they were first released. Add a table here when giving it a new column.
     * Only these are checked since H2 can't list columns for the "user" table (it's a reserved word).
     */
    private val migratedTables = arrayOf(
        TicketTable
    )

    /**
     * Creates missing tables, adds columns that newer versions brought in, then runs data fixes.
     * Safe to run on every start.
     */
    fun initDatabase() {
        transaction {
            SchemaUtils.create(*tables)

            // New columns all have defaults or are nullable so they can be added to existing rows
            SchemaUtils.addMissingColumnsStatements(*migratedTables).forEach { statement -> exec(statement) }

            // Indexes on existing tables aren't added by the above
            exec(
                "CREATE UNIQUE INDEX IF NOT EXISTS ${TicketTable.NUMBER_INDEX} " +
                    "ON ${TicketTable.tableName} (${TicketTable.numberYear.name}, ${TicketTable.numberSequence.name})"
            )

            AppLogger.m(INIT_TAG, SimpleResult.SUCCESS)
        }

        ticketDatabase.backfillNumbers()
    }

    /** Total size of the database files on disk */
    fun sizeOnDisk(): Long =
        databaseDirectory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Writes a zip of the live database to [destination] using H2's online backup
     * @return Whether the backup was written
     */
    fun backupTo(destination: File): Boolean {
        return try {
            transaction {
                // Path is ours not the user's, but quotes would still break the statement
                exec("BACKUP TO '${destination.absolutePath.replace("'", "''")}'")
            }

            AppLogger.m(BACKUP_TAG, SimpleResult.SUCCESS)
            destination.exists()
        } catch (ex: Exception) {
            AppLogger.e(BACKUP_TAG, SimpleResult.FAIL, ex)
            false
        }
    }
}

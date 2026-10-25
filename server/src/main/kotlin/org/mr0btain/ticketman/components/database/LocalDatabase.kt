package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.DataFixTable
import org.mr0btain.ticketman.components.database.tables.audit.AuditLogTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerNoteTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.components.database.tables.image.ImageTable
import org.mr0btain.ticketman.components.database.tables.location.InventoryStockTable
import org.mr0btain.ticketman.components.database.tables.location.StockTransferTable
import org.mr0btain.ticketman.components.database.tables.location.LocationTable
import org.mr0btain.ticketman.components.database.tables.schedule.ScheduleTable
import org.mr0btain.ticketman.components.database.tables.schedule.TimeOffAllowanceTable
import org.mr0btain.ticketman.components.database.tables.schedule.TimeOffRequestTable
import org.jetbrains.exposed.v1.core.isNull
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryImageTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceChargeTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoicePaymentTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryNoteTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.components.database.tables.note.NoteImageTable
import org.mr0btain.ticketman.components.database.tables.note.NoteTable
import org.mr0btain.ticketman.components.database.tables.shop.ShopTable
import org.mr0btain.ticketman.components.database.tables.station.StationTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketNoteTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketSignatureTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketWorkTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketStatusHistoryTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.notify.NotifySettingsTable
import org.mr0btain.ticketman.components.database.tables.payment.CardChargeTable
import org.mr0btain.ticketman.components.database.tables.payment.PaymentSettingsTable
import org.mr0btain.ticketman.components.database.tables.notify.OutboxTable
import org.mr0btain.ticketman.components.database.tables.estimate.EstimateTable
import org.mr0btain.ticketman.components.database.tables.storedvalue.CustomerCreditTable
import org.mr0btain.ticketman.components.database.tables.storedvalue.GiftCardTable
import org.mr0btain.ticketman.components.database.tables.purchase.PurchaseOrderTable
import org.mr0btain.ticketman.components.database.tables.purchase.SupplierTable
import org.mr0btain.ticketman.components.database.tables.device.CustomerDeviceTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketPasscodeTable
import org.mr0btain.ticketman.components.database.tables.report.CommissionRateTable
import org.mr0btain.ticketman.components.database.tables.appointment.AppointmentTable
import org.mr0btain.ticketman.components.database.tables.timeclock.TimeEntryTable
import org.mr0btain.ticketman.components.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.user.InviteTable
import org.mr0btain.ticketman.components.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.components.image.ImageStore
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.io.File

class LocalDatabase (
    appDir: String
) {
    private val TAG = "ServerDatabase"
    private val INIT_TAG = "$TAG:Init"
    private val BACKUP_TAG = "$TAG:Backup"
    private val DETACHED_NOTES_TAG = "$TAG:RemoveDetachedNotes"
    private val DATA_FIX_TAG = "$TAG:DataFix"

    /** Folder holding the H2 files */
    val databaseDirectory = File("$appDir/database")

    /** Shuts H2 down cleanly, writing everything out and letting go of its files. Run when the server stops */
    fun close() {
        try {
            transaction(db) { exec("SHUTDOWN") }
        } catch (_: Exception) {
            // Committing after SHUTDOWN fails, the database is closed either way
        }
        AppLogger.m(TAG, "Database closed")
    }

    /** Photo files, kept out of H2 so the database stays small and quick to back up */
    val imageStore = ImageStore(File("$appDir/images"))

    // Connect to the local database. Not closed by H2's own shutdown hook, which would race requests still being
    // answered while the server stops; close() does it once Ktor has stopped
    private val db = ExposedDatabase.connect("jdbc:h2:$appDir/database/database;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;", "org.h2.Driver")

    //Database.connect("jdbc:sqlite:$appDir/testData.db", "org.sqlite.JDBC")
    // Database.connect("jdbc:h2:$appDir/testData.db", "org.h2.Driver")
    //Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")

    val customerDatabase = CustomerDatabase()
    val ticketDatabase = TicketDatabase()
    val tokenDatabase = TokenDatabase()
    val inventoryDatabase = InventoryDatabase()
    val userDatabase = UserDatabase()
    val auditLogDatabase = AuditLogDatabase(userDatabase)
    val shopDatabase = ShopDatabase()
    val statsDatabase = StatsDatabase()
    val counterDatabase = CounterDatabase(ticketDatabase, customerDatabase)
    val inviteDatabase = InviteDatabase()
    val stationDatabase = StationDatabase()
    val invoiceDatabase = InvoiceDatabase()
    val workDatabase = WorkDatabase()
    val notifyDatabase = NotifyDatabase()
    val estimateDatabase = EstimateDatabase()
    val storedValueDatabase = StoredValueDatabase()
    val purchaseDatabase = PurchaseDatabase()
    val deviceDatabase = DeviceDatabase()
    val passcodeDatabase = PasscodeDatabase()
    val reportDatabase = ReportDatabase(invoiceDatabase)
    val appointmentDatabase = AppointmentDatabase()
    val timeClockDatabase = TimeClockDatabase()
    val locationDatabase = LocationDatabase()
    val scheduleDatabase = ScheduleDatabase()
    val timeOffDatabase = TimeOffDatabase()
    val paymentDatabase = PaymentDatabase()
    val imageDatabase = ImageDatabase { recordDatabases }

    /**
     * Every record type's database. A new record type goes here, and the photo sweep and the detached note clean up
     * pick up its notes from it
     */
    val recordDatabases: List<Database<*>> = listOf(customerDatabase, ticketDatabase, inventoryDatabase, userDatabase)

    // Every table, in an order where references come before the tables that use them
    private val tables = arrayOf(
        CustomerTable,
        CustomerNoteTable,
        CustomerTicketTable,
        TicketTable,
        TicketNoteTable,
        TicketStatusHistoryTable,
        TicketSignatureTable,
        TicketWorkTable,
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
        AuditLogTable,
        InviteTable,
        StationTable,
        InvoiceTable,
        InvoiceChargeTable,
        InvoicePaymentTable,
        NotifySettingsTable,
        OutboxTable,
        EstimateTable,
        CustomerCreditTable,
        GiftCardTable,
        SupplierTable,
        PurchaseOrderTable,
        CustomerDeviceTable,
        TicketPasscodeTable,
        CommissionRateTable,
        AppointmentTable,
        TimeEntryTable,
        PaymentSettingsTable,
        CardChargeTable,
        LocationTable,
        InventoryStockTable,
        StockTransferTable,
        ScheduleTable,
        TimeOffAllowanceTable,
        TimeOffRequestTable,
        DataFixTable
    )

    /**
     * Tables that have gained columns since they were first released. Add a table here when giving it a new column.
     * Only these are checked since H2 can't list columns for the "user" table (it's a reserved word).
     */
    private val migratedTables = arrayOf(
        TicketTable,
        TokenTable,
        InventoryTable,
        StationTable,
        ImageTable,
        InvoiceTable,
        NoteTable,
        TicketStatusHistoryTable,
        CustomerTable,
        InvoicePaymentTable,
        InvoiceChargeTable,
        EstimateTable,
        AppointmentTable,
        PurchaseOrderTable,
        TimeEntryTable,
        ScheduleTable
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

            addUserColumns()

            // Indexes on existing tables aren't added by the above
            exec(
                "CREATE UNIQUE INDEX IF NOT EXISTS ${TicketTable.NUMBER_INDEX} " +
                    "ON ${TicketTable.tableName} (${TicketTable.numberPrefix.name}, ${TicketTable.numberYear.name}, ${TicketTable.numberSequence.name})"
            )
            exec(
                "CREATE UNIQUE INDEX IF NOT EXISTS ${TicketTable.STATUS_TOKEN_INDEX} " +
                    "ON ${TicketTable.tableName} (${TicketTable.statusToken.name})"
            )

            AppLogger.m(INIT_TAG, SimpleResult.SUCCESS)
        }

        ticketDatabase.backfillNumbers()
        ticketDatabase.backfillStatusTokens()
        userDatabase.migrateDeactivatedUsers()
        tokenDatabase.clearLegacySessions()
        tokenDatabase.backfillExpiry()
        inventoryDatabase.migrateLegacyPrices()
        runOnce("legacy-names") {
            customerDatabase.migrateLegacyNames()
            userDatabase.migrateLegacyNames()
        }
        runOnce("invoice-ticket-key") { dropInvoiceTicketKey() }
        // Counter sales have no ticket. H2's unique index allows any number of nulls
        runOnce("invoice-ticket-nullable") {
            TransactionManager.current().exec("ALTER TABLE ${InvoiceTable.tableName} ALTER COLUMN ${InvoiceTable.ticket.name} SET NULL")
        }
        // Devices are kept per customer since; older tickets get theirs from what was typed on them
        runOnce("customer-devices") { deviceDatabase.backfill() }
        // Locations: numbers are unique per location's code, stock is counted per location, and everything from
        // before belongs to the main one
        runOnce("number-prefix-indexes") { prefixNumberIndexes() }
        val main = locationDatabase.ensureMain()
        runOnce("stock-per-location") { inventoryDatabase.moveStockTo(main.identifier) }
        assignMainLocation(main.identifier)
        removeDetachedNotes()
    }

    /**
     * Runs [fix] unless it already ran on this database, for fixes that would wrongly change data typed later if
     * run every start. The fix and the note that it ran are one transaction, so a failed fix runs again next start
     */
    private fun runOnce(name: String, fix: () -> Unit) {
        try {
            transaction {
                if (DataFixTable.selectAll().where { DataFixTable.name eq name }.empty()) {
                    fix()
                    DataFixTable.insert { row ->
                        row[DataFixTable.name] = name
                        row[ranAt] = System.currentTimeMillis()
                    }
                    AppLogger.m(DATA_FIX_TAG, "Ran $name")
                }
            }
        } catch (ex: Exception) {
            AppLogger.e(DATA_FIX_TAG, "$name: ${SimpleResult.FAIL}", ex)
        }
    }

    /**
     * Invoices used to be deleted with their ticket by a foreign key. Now one with money against it outlives the
     * ticket, so the key is dropped; the column and its unique index stay
     */
    private fun dropInvoiceTicketKey() {
        val transaction = TransactionManager.current()
        val keys = transaction.exec(
            "SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k ON k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME " +
                "AND k.TABLE_SCHEMA = tc.TABLE_SCHEMA " +
                "WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY' AND UPPER(tc.TABLE_NAME) = 'INVOICES' " +
                "AND UPPER(k.COLUMN_NAME) = 'TICKET_ID'"
        ) { result -> buildList { while (result.next()) add(result.getString(1)) } }.orEmpty()

        keys.forEach { name -> transaction.exec("ALTER TABLE ${InvoiceTable.tableName} DROP CONSTRAINT \"$name\"") }
    }

    /**
     * Numbers were unique per year; with locations they're unique per location's code and year. The old indexes go
     * and the new ones take their place (a fresh database already has them)
     */
    private fun prefixNumberIndexes() {
        val transaction = TransactionManager.current()
        listOf(
            Triple(TicketTable.NUMBER_INDEX, TicketTable.tableName, listOf(TicketTable.numberPrefix, TicketTable.numberYear, TicketTable.numberSequence)),
            Triple(InvoiceTable.NUMBER_INDEX, InvoiceTable.tableName, listOf(InvoiceTable.numberPrefix, InvoiceTable.numberYear, InvoiceTable.numberSequence)),
            Triple(PurchaseOrderTable.NUMBER_INDEX, PurchaseOrderTable.tableName, listOf(PurchaseOrderTable.numberPrefix, PurchaseOrderTable.numberYear, PurchaseOrderTable.numberSequence))
        ).forEach { (index, table, columns) ->
            transaction.exec("DROP INDEX IF EXISTS $index")
            transaction.exec("CREATE UNIQUE INDEX $index ON $table (${columns.joinToString { it.name }})")
        }
    }

    /**
     * Gives rows without a location the main one: everything from before locations, and anything written by an
     * older client's request that didn't say. Run every start, it only touches rows that need it
     */
    private fun assignMainLocation(main: kotlin.uuid.Uuid) {
        try {
            transaction {
                listOf(TicketTable.location, InvoiceTable.location, StationTable.location, AppointmentTable.location, PurchaseOrderTable.location, TimeEntryTable.location)
                    .forEach { column ->
                        column.table.update(where = { column.isNull() }) { row -> row[column] = main }
                    }
            }
        } catch (ex: Exception) {
            AppLogger.e(DATA_FIX_TAG, "Main location: ${SimpleResult.FAIL}", ex)
        }
    }

    /**
     * Deletes notes no record links to, left by removing customers, tickets and items before removing took their
     * notes along. Their photos are picked up by the next image sweep
     */
    private fun removeDetachedNotes() {
        try {
            val removed = transaction {
                val attached = attachedNotes(recordDatabases)
                val detached = NoteTable.select(NoteTable.id).map { it[NoteTable.id].value }.filter { it !in attached }

                detached.chunked(500).forEach { ids -> NoteTable.deleteWhere { NoteTable.id inList ids } }
                detached.size
            }

            if (removed > 0) AppLogger.m(DETACHED_NOTES_TAG, "Removed $removed notes with no record")
        } catch (ex: Exception) {
            AppLogger.e(DETACHED_NOTES_TAG, SimpleResult.FAIL, ex)
        }
    }

    /**
     * Adds columns the user table gained since release. Done by hand because H2 can't list the columns of a
     * table named "user", which Exposed needs for addMissingColumnsStatements
     */
    private fun JdbcTransaction.addUserColumns() {
        listOf(
            UserTable.status to "DEFAULT '${UserStatus.ACTIVE.name}' NOT NULL",
            UserTable.needsSetup to "DEFAULT FALSE NOT NULL",
            UserTable.pinHash to "DEFAULT '' NOT NULL",
            UserTable.pinFailedAttempts to "DEFAULT 0 NOT NULL",
            UserTable.pinLocked to "DEFAULT FALSE NOT NULL",
            UserTable.photo to "NULL",
            UserTable.badgeCode to "NULL",
            UserTable.homeLocation to "NULL"
        ).forEach { (column, constraints) ->
            exec(
                "ALTER TABLE ${identity(UserTable)} ADD COLUMN IF NOT EXISTS " +
                    "${identity(column)} ${column.columnType.sqlType()} $constraints"
            )
        }
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

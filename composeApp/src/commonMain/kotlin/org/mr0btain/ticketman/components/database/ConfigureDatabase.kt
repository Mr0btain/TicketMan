package org.mr0btain.ticketman.components.database

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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


/**
 * Initialises the client database
 */

fun initDatabase() {
    val databaseDir = FileKit.databasesDir.path
    val db = Database.connect("jdbc:h2:$databaseDir/client/database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    /**
     * Initialize the database in case it were dealing with a new install or something happened to database
     */
    try {
        transaction {
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
                SettingsTable
            )
        }

        AppLogger.m("ClientDatabase: Initialize", SimpleResult.SUCCESS)
    } catch (ex: Exception) {
        AppLogger.e(" Initialize", SimpleResult.SUCCESS, ex)
    }
}
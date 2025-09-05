package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.toImage
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketNoteTable
import org.mr0btain.ticketman.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.database.tables.ticket.toTicket
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.collections.plus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class TicketDatabaseImpl(
    private val shouldLog: Boolean = false
) : Database<Ticket>(
    tag = "TicketLocalDatabase",
    shouldLog = shouldLog
) {
    /** Gets all items from database */
    override fun all(): List<Ticket>? {
        var result = listOf<Ticket>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = TicketEntity.all().map { item -> item.toTicket() }

                AppLogger.m(GET_ALL_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

    /** Gets an item by its identifier */
    override fun getById(identifier: Uuid): Ticket? {
        var result: Ticket? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = TicketEntity.findById(identifier.toJavaUuid())?.toTicket()

                AppLogger.m(GET_BY_ID_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(GET_BY_ID_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<Ticket> {
        val result = mutableListOf<Ticket>()
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result.addAll(
                    TicketEntity.all()
                        .orderBy(TicketTable.time to SortOrder.ASC) // or ASC
                        .offset(offset)
                        .limit(pageSize)
                        .map { it.toTicket() }
                )
            }

            AppLogger.m(GET_PAGED_ITEMS, SimpleError.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.i(GET_PAGED_ITEMS, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return result
    }

    override fun count(): Long {
        var result = 0L

        transaction {
            result = TicketEntity.count()
        }

        return result
    }

    /** Adds all items to the database */
    override fun addAll(data: List<Ticket>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                data.forEach { item ->
                    // Convert kotlin uuid to java for database
                    val identifier = item.identifier.toJavaUuid()

                    // Insert into database
                    TicketEntity.new(identifier) {
                        device = item.device
                        status = item.status
                        priority = item.priority
                        time = item.time
                    }
                }

                AppLogger.m(ADD_ALL_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Removes an item from the database */
    override fun remove(identifier: Uuid): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketEntity.findById(identifier.toJavaUuid())?.delete()

                AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.m(REMOVE_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Adds an item to the database */
    override fun add(data: Ticket): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Convert kotlin uuid to java for database
                val identifier = data.identifier.toJavaUuid()

                // Insert into database
                TicketEntity.new(identifier) {
                    device = data.device
                    status = data.status
                    priority = data.priority
                    time = data.time
                }

                AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(ADD_TAG, SimpleError.FAIL)
            return false
        }
    }

    /** Updates an item in the database */
    override fun update(data: Ticket): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketEntity
                    .findByIdAndUpdate(data.identifier.toJavaUuid()) { item ->
                        item.device = data.device
                        item.status = data.status
                        item.priority = data.priority
                        item.time = data.time
                    }

                AppLogger.m(UPDATE_TAG, SimpleError.SUCCESS)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.i(UPDATE_TAG, SimpleError.FAIL)
            return false
        }
    }

    override fun addNote(identifier: Uuid, note: Note, images: List<StoredImage>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Insert the note into database and get its entity
                insertNote(note, images)?.let { noteEntity ->
                    // Add note to customer notes
                    TicketEntity.findByIdAndUpdate(identifier.toJavaUuid()) { ticketEntity ->
                        ticketEntity.notes = SizedCollection(ticketEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleError.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, "Ticket not found! $identifier")
                } ?: AppLogger.i(ADD_NOTE_TAG, "Note entity was null!")
            }

            return true
        } catch (ex: Exception) {
            AppLogger.i(ADD_NOTE_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return false
    }

    override fun notes(identifier: Uuid): Map<Note, List<StoredImage>> {
        val result = mutableMapOf<Note, List<StoredImage>>()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                TicketEntity.findById(identifier.toJavaUuid())?.let { ticketEntity ->
                    ticketEntity.notes.forEach { noteEntity ->
                        result[noteEntity.toNote()] = noteEntity.images.map { it.toImage() }
                    }

                    AppLogger.m(GET_NOTES_TAG, SimpleError.SUCCESS)
                } ?: AppLogger.i(GET_NOTES_TAG, "Could not find inventory item!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_NOTES_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return result
    }

    override fun removeNote(identifier: Uuid): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Find the note if it exists
                val note = NoteEntity.findById(identifier.toJavaUuid()) ?: return@transaction

                // No clue if this actually works or not??
                TicketTable
                    .join(
                        TicketNoteTable, onColumn = TicketTable.id, otherColumn = TicketNoteTable.ticket,
                        joinType = JoinType.INNER,
                    )
                    .select(TicketNoteTable.note eq note.id)
                    .limit(1)
                    .map { TicketEntity.wrapRow(it) }
                    .firstOrNull()
                    ?.let { ticketEntity ->
                        ticketEntity.notes = SizedCollection(ticketEntity.notes.filter { it != note })
                    }

                // Delete linked images
                note.images.forEach { it.delete() }

                // Delete the note (Exposed will remove NoteImageTable entries automatically)
                note.delete()
            }

            AppLogger.m(REMOVE_NOTE_TAG, SimpleError.SUCCESS)

            return true
        } catch (ex: Exception) {
            AppLogger.m(REMOVE_NOTE_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return false
    }
}
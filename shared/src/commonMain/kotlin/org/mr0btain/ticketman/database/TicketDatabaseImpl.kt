package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.toStoredImage
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.database.tables.ticket.toTicket
import org.mr0btain.ticketman.error.NoteError
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class TicketDatabaseImpl() : Database<Ticket>("TicketLocalDatabase") {
    override fun all(): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_ALL_TAG) {
            transaction {
                result = TicketEntity.all().map { item -> item.toTicket() }
            }

            if (result != null)
                AppLogger.m(GET_ALL_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_ALL_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun getById(identifier: Uuid): Ticket? {
        var result: Ticket? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                TicketEntity.findById(identifier.toJavaUuid())?.let { ticketEntity ->
                    result = ticketEntity.toTicket()
                    AppLogger.m(GET_BY_ID_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(GET_BY_ID_TAG, TicketError.NOT_FOUND)
            }
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = TicketEntity.all()
                    .orderBy(TicketTable.time to SortOrder.DESC) // or ASC
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toTicket() }
            }

            if (result != null)
                AppLogger.m(GET_PAGED_ITEMS_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_PAGED_ITEMS_TAG, SimpleResult.FAIL)
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

    override fun addAll(data: List<Ticket>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
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

                AppLogger.m(ADD_ALL_TAG, SimpleResult.SUCCESS)
            }
        }
    }

    override fun remove(identifier: Uuid) {
        appLogTryCatch(REMOVE_TAG) {
            transaction {
                TicketEntity.findById(identifier.toJavaUuid())?.let { ticketEntity ->
                    AppLogger.m(REMOVE_TAG, SimpleResult.SUCCESS)
                    ticketEntity.delete()
                } ?: AppLogger.i(REMOVE_TAG, TicketError.NOT_FOUND)
            }
        }
    }

    override fun add(data: Ticket) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                TicketEntity.new(data.identifier.toJavaUuid()) {
                    device = data.device
                    status = data.status
                    priority = data.priority
                    time = data.time
                }

                AppLogger.m(ADD_TAG, SimpleResult.SUCCESS)
            }
        }
    }

    override fun update(data: Ticket) {
        appLogTryCatch(UPDATE_TAG) {
            transaction {
                TicketEntity
                    .findByIdAndUpdate(data.identifier.toJavaUuid()) { item ->
                        item.device = data.device
                        item.status = data.status
                        item.priority = data.priority
                        item.time = data.time

                        AppLogger.m(UPDATE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(UPDATE_TAG, TicketError.NOT_FOUND)
            }
        }
    }

    override fun addNote(identifier: Uuid, note: Note, storedImages: List<StoredImage>) {
        appLogTryCatch(ADD_NOTE_TAG) {
            transaction {
                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to customer notes
                    TicketEntity.findByIdAndUpdate(identifier.toJavaUuid()) { ticketEntity ->
                        ticketEntity.notes = SizedCollection(ticketEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, TicketError.NOT_FOUND)
                }
            }
        }
    }

    override fun getNotes(identifier: Uuid): Map<Note, List<StoredImage>>? {
        var result: Map<Note, List<StoredImage>>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                TicketEntity.findById(identifier.toJavaUuid())?.let { ticketEntity ->
                    result = ticketEntity.notes.associate { noteEntity ->
                        noteEntity.toNote() to noteEntity.images.map { it.toStoredImage() }
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, TicketError.NOT_FOUND)
            }

            if (result != null)
                AppLogger.m(GET_NOTES_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_NOTES_TAG, TicketError.NOT_FOUND)
        }

        return result
    }
}
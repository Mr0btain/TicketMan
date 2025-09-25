package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import java.util.*

class TicketDatabaseImpl() : Database<Ticket>("TicketLocalDatabase") {
    override fun all(): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_ALL_TAG) {
            transaction {
                result = TicketEntity.all().map { item -> item.toTicket() }
            }

            result != null
        }

        return result
    }

    override fun getById(identifier: UUID): Ticket? {
        var result: Ticket? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    result = ticketEntity.toTicket()
                } ?: AppLogger.i(GET_BY_ID_TAG, TicketError.NOT_FOUND)

                result != null
            }
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = TicketEntity.all()
                    .orderBy(TicketTable.id to SortOrder.DESC) // or ASC
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toTicket() }
            }

            result != null
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = TicketEntity.count()
                true
            }
        }

        return count
    }

    override fun addAll(data: List<Ticket>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    TicketEntity.new(item.identifier) {
                        device = item.device
                        status = item.status
                        priority = item.priority
                    }
                }

                true
            }
        }
    }

    override fun remove(identifier: UUID) {
        appLogTryCatch(REMOVE_TAG) {
            var result = false

            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    ticketEntity.delete()
                    result = true
                } ?: AppLogger.i(REMOVE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    override fun search(query: String, limit: Int): List<SearchResult>? {
        return appLogTryCatch<List<SearchResult>?>(SEARCH_TAG) {
            transaction {
                val pattern = "$query%"

                Pair(TicketEntity
                    .find { (TicketTable.id.castTo(VarCharColumnType()) like pattern) }
                    .orderBy(TicketTable.id to SortOrder.DESC)
                    .limit(limit)
                    .map { ticketEntity ->
                        SearchResult(
                            identifier = ticketEntity.id.value,
                            text = ticketEntity.id.value.toString()
                        )
                    },
                    true
                    )
            }
        }
    }

    override fun add(data: Ticket) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                TicketEntity.new(data.identifier) {
                    device = data.device
                    status = data.status
                    priority = data.priority
                }

                true
            }
        }
    }

    override fun update(data: Ticket) {
        appLogTryCatch(UPDATE_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(data.identifier) { item ->
                        item.device = data.device
                        item.status = data.status
                        item.priority = data.priority

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    override fun addNote(identifier: UUID, note: Note, storedImages: List<StoredImage>) {
        appLogTryCatch(ADD_NOTE_TAG) {
            var result = false

            transaction {
                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to customer notes
                    TicketEntity.findByIdAndUpdate(identifier) { ticketEntity ->
                        ticketEntity.notes = SizedCollection(ticketEntity.notes + noteEntity)

                        result = true
                    } ?: AppLogger.i(ADD_NOTE_TAG, TicketError.NOT_FOUND)
                }
            }

            result
        }
    }

    override fun getNotes(identifier: UUID): Map<Note, List<StoredImage>>? {
        var result: Map<Note, List<StoredImage>>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    result = ticketEntity.notes.associate { noteEntity ->
                        noteEntity.toNote() to noteEntity.images.map { it.toStoredImage() }
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, TicketError.NOT_FOUND)
            }

            result != null
        }

        return result
    }
}
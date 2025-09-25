package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.user.UserEntity
import org.mr0btain.ticketman.database.tables.user.UserTable
import org.mr0btain.ticketman.error.NoteError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.UserError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.util.*

@Suppress("PrivatePropertyName")
class UserDatabaseImpl(tag: String = "UserLocalDatabase") : Database<User>(tag) {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"

    override fun all(): List<User>? {
        var result: List<User>? = null

        appLogTryCatch(GET_ALL_TAG) {
            // Go into database and convert data to customer data item
            transaction {
                result = UserEntity.all().map { item -> item.toUser() }
            }

            result != null
        }

        return result
    }

    override fun getById(identifier: UUID): User? {
        var result: User? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    result = userEntity.toUser()
                } ?: AppLogger.i(GET_BY_ID_TAG, UserError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<User>? {
        var result: List<User>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = UserEntity
                    .all()
                    .orderBy(UserTable.name to SortOrder.ASC, UserTable.id to SortOrder.ASC)
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toUser() }
            }

            result != null
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = UserEntity.count()
                true
            }
        }
        return count
    }

    override fun addAll(data: List<User>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    UserEntity.new(item.identifier) {
                        username = item.username
                        password = item.password
                        name = item.name.toString()
                        contactInfo = item.contactInfo
                        access = item.accessLevel
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
                UserEntity.findById(identifier)?.let { userEntity ->
                    userEntity.delete()
                    result = true
                } ?: AppLogger.i(REMOVE_TAG, UserError.NOT_FOUND)

                result
            }
        }
    }

    override fun search(query: String, limit: Int): List<SearchResult>? {
        return appLogTryCatch<List<SearchResult>?>(SEARCH_TAG) {
            transaction {
                val pattern = "$query%"

                Pair(
                    UserEntity
                        .find {
                            (UserTable.name like pattern) or
                                    (UserTable.id.castTo(VarCharColumnType()) like pattern) or
                                    (UserTable.username like pattern)
                        }
                        .orderBy(UserTable.name to SortOrder.ASC, UserTable.id to SortOrder.ASC)
                        .limit(limit)
                        .map { userEntity ->
                            SearchResult(
                                identifier = userEntity.id.value,
                                text = when {
                                    userEntity.name.startsWith(query) -> userEntity.name
                                    userEntity.username.startsWith(query) -> userEntity.username
                                    userEntity.id.value.toString().startsWith(query) -> userEntity.id.value.toString()
                                    else -> userEntity.name
                                }
                            )
                        },
                    true
                )
            }
        }
    }

    override fun add(data: User) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                UserEntity.new(data.identifier) {
                    username = data.username
                    password = data.password
                    name = data.name.toString()
                    contactInfo = data.contactInfo
                    access = data.accessLevel
                }

                true
            }
        }
    }

    override fun update(data: User) {
        appLogTryCatch(UPDATE_TAG) {
            var result = false

            transaction {
                UserEntity
                    .findById(data.identifier)
                    ?.apply {
                        username = data.username
                        password = data.password
                        name = data.name.toString()
                        contactInfo = data.contactInfo
                        access = data.accessLevel

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, NoteError.NOT_FOUND)
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
                    UserEntity.findByIdAndUpdate(identifier) { userEntity ->
                        userEntity.notes = SizedCollection(userEntity.notes + noteEntity)

                        result = true
                    } ?: AppLogger.i(ADD_NOTE_TAG, NoteError.NOT_FOUND)
                }
            }

            result
        }
    }

    override fun getNotes(identifier: UUID): Map<Note, List<StoredImage>>? {
        var result: Map<Note, List<StoredImage>>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    result = userEntity.notes.associate { noteEntity ->
                        noteEntity.toNote() to noteEntity.images.map { it.toStoredImage() }
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, UserError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    /**
     * Adds a ticket to a user, assuming that ticket and user already exist in the database
     * @param userId Identifier of the user
     * @param ticketId Identifier of the ticket
     */
    fun addTicket(userId: UUID, ticketId: UUID) {
        appLogTryCatch(ADD_TICKET_TAG) {
            var result = false
            transaction {
                // Get ticket entity
                TicketEntity.findById(ticketId)?.let { ticketEntity ->
                    // Get user entity
                    UserEntity.findById(userId)?.let { userEntity ->
                        // Insert ticket into user links
                        userEntity.tickets = SizedCollection(userEntity.tickets + ticketEntity)

                        result = true
                    } ?: AppLogger.i(ADD_TICKET_TAG, UserError.NOT_FOUND)
                } ?: AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    /**
     *  Gets all tickets for a user
     *  @param identifier Identifier of the user
     */
    fun getTickets(identifier: UUID): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_TICKETS_TAG) {
            transaction {
                UserEntity.findById(identifier)?.let { userEntity ->
                    result = userEntity.tickets
                        .map { ticketEntity -> ticketEntity.toTicket() }
                } ?: AppLogger.i(GET_TICKETS_TAG, UserError.NOT_FOUND)
            }

            result != null
        }

        return result
    }
}
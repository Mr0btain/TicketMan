package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.toStoredImage
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.toTicket
import org.mr0btain.ticketman.database.tables.user.UserEntity
import org.mr0btain.ticketman.database.tables.user.UserTable
import org.mr0btain.ticketman.database.tables.user.toUser
import org.mr0btain.ticketman.error.*
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Suppress("PrivatePropertyName")
@OptIn(ExperimentalUuidApi::class)
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

            if (result != null)
                AppLogger.m(GET_ALL_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_ALL_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun getById(identifier: Uuid): User? {
        var result: User? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                UserEntity.findById(identifier.toJavaUuid())?.let { userEntity ->
                    result = userEntity.toUser()
                    AppLogger.m(GET_BY_ID_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(GET_BY_ID_TAG, UserError.NOT_FOUND)
            }
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<User>? {
        var result: List<User>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = UserEntity
                    .all()
                    .orderBy(UserTable.name to SortOrder.ASC, UserTable.time to SortOrder.ASC)
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toUser() }
            }

            if (result != null)
                AppLogger.m(GET_PAGED_ITEMS_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_PAGED_ITEMS_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun count(): Long = UserEntity.count()

    override fun addAll(data: List<User>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    UserEntity.new(item.identifier.toJavaUuid()) {
                        username = item.username
                        password = item.password
                        name = item.name
                        contactInfo = item.contactInfo
                        access = item.accessLevel
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
                UserEntity.findById(identifier.toJavaUuid())?.let { userEntity ->
                    userEntity.delete()
                    AppLogger.m(REMOVE_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(REMOVE_TAG, UserError.NOT_FOUND)


            }
        }
    }

    override fun add(data: User) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                UserEntity.new(data.identifier.toJavaUuid()) {
                    username = data.username
                    password = data.password
                    name = data.name
                    contactInfo = data.contactInfo
                    access = data.accessLevel
                    time = data.time
                }

                AppLogger.m(ADD_TAG, SimpleResult.SUCCESS)
            }
        }
    }

    override fun update(data: User) {
        appLogTryCatch(UPDATE_TAG) {
            transaction {
                UserEntity
                    .findById(data.identifier.toJavaUuid())
                    ?.apply {
                        username = data.username
                        password = data.password
                        name = data.name
                        contactInfo = data.contactInfo
                        access = data.accessLevel
                        time = data.time

                        AppLogger.m(UPDATE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(UPDATE_TAG, NoteError.NOT_FOUND)
            }
        }
    }

    override fun addNote(identifier: Uuid, note: Note, storedImages: List<StoredImage>) {
        appLogTryCatch(ADD_NOTE_TAG) {
            transaction {
                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to customer notes
                    UserEntity.findByIdAndUpdate(identifier.toJavaUuid()) { userEntity ->
                        userEntity.notes = SizedCollection(userEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, NoteError.NOT_FOUND)
                }
            }
        }
    }

    override fun getNotes(identifier: Uuid): Map<Note, List<StoredImage>>? {
        var result: Map<Note, List<StoredImage>>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                UserEntity.findById(identifier.toJavaUuid())?.let { userEntity ->
                    result = userEntity.notes.associate { noteEntity ->
                        noteEntity.toNote() to noteEntity.images.map { it.toStoredImage() }
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, UserError.NOT_FOUND)
            }

            if (result != null)
                AppLogger.m(GET_NOTES_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_NOTES_TAG, SimpleResult.FAIL)
        }

        return result
    }

    /**
     * Adds a ticket to a user, assuming that ticket and user already exist in the database
     * @param userId Identifier of the user
     * @param ticketId Identifier of the ticket
     */
    fun addTicket(userId: Uuid, ticketId: Uuid) {
        appLogTryCatch(ADD_TICKET_TAG) {
            transaction {
                // Get ticket entity
                TicketEntity.findById(ticketId.toJavaUuid())?.let { ticketEntity ->
                    // Get user entity
                    UserEntity.findById(userId.toJavaUuid())?.let { userEntity ->
                        // Insert ticket into user links
                        userEntity.tickets = SizedCollection(userEntity.tickets + ticketEntity)

                        AppLogger.m(ADD_TICKET_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(ADD_TICKET_TAG, UserError.NOT_FOUND)
                } ?: AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND)
            }
        }
    }

    /**
     *  Gets all tickets for a user
     *  @param identifier Identifier of the user
     */
    fun getTickets(identifier: Uuid): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_TICKETS_TAG) {
            transaction {
                UserEntity.findById(identifier.toJavaUuid())?.let { userEntity ->
                    result = userEntity.tickets
                        .map { ticketEntity -> ticketEntity.toTicket() }
                } ?: AppLogger.i(GET_TICKETS_TAG, UserError.NOT_FOUND)
            }

            if (result != null)
                AppLogger.m(GET_TICKETS_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_TICKETS_TAG, SimpleResult.FAIL)
        }

        return result
    }
}
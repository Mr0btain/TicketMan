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
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.toStoredImage
import org.mr0btain.ticketman.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.toTicket
import org.mr0btain.ticketman.database.tables.user.UserEntity
import org.mr0btain.ticketman.database.tables.user.UserNoteTable
import org.mr0btain.ticketman.database.tables.user.UserTable
import org.mr0btain.ticketman.database.tables.user.toUser
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class UserDatabaseImpl(
    tag: String = "UserLocalDatabase",
    private val shouldLog: Boolean = false
) : Database<User>(
    tag = tag,
    shouldLog = shouldLog
) {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"

    /** Gets all items from database */
    override fun all(): List<User>? {
        var result = listOf<User>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserEntity.all().map { item -> item.toUser() }

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
    override fun getById(identifier: Uuid): User? {
        var result: User? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = UserEntity.findById(identifier.toJavaUuid())?.toUser()

                AppLogger.m(GET_BY_ID_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(GET_BY_ID_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<User> {
        val result = mutableListOf<User>()

        try {
            transaction {
                result.addAll(
                    UserEntity
                        .all()
                        .orderBy(UserTable.name to SortOrder.ASC, InventoryTable.time to SortOrder.ASC)
                        .offset(offset)
                        .limit(pageSize)
                        .map { it.toUser() }
                )
            }

            AppLogger.m(GET_PAGED_ITEMS_TAG, SimpleError.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.i(GET_PAGED_ITEMS_TAG, SimpleError.FAIL)
        }

        return result
    }

    override fun count(): Long = UserEntity.count()

    /** Adds all items to the database */
    override fun addAll(data: List<User>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                data.forEach { item ->
                    // Convert kotlin uuid to java for database
                    val identifier = item.identifier.toJavaUuid()

                    // Insert into database
                    UserEntity.new(identifier) {
                        username = item.username
                        password = item.password
                        name = item.name
                        contactInfo = item.contactInfo
                        access = item.accessLevel
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

                UserEntity.findById(identifier.toJavaUuid())?.delete()

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
    override fun add(data: User): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Convert kotlin uuid to java for database
                val identifier = data.identifier.toJavaUuid()

                // Insert into database
                UserEntity.new(identifier) {
                    username = data.username
                    password = data.password
                    name = data.name
                    contactInfo = data.contactInfo
                    access = data.accessLevel
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
    override fun update(data: User): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserEntity
                    .findById(data.identifier.toJavaUuid())
                    ?.apply {
                        username = data.username
                        password = data.password
                        name = data.name
                        contactInfo = data.contactInfo
                        access = data.accessLevel
                        time = data.time
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

    /** Adds a ticket to a user, assuming that ticket and user already exist in the database */
    fun addTicket(userId: Uuid, ticketId: Uuid) {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Get ticket entity
                TicketEntity.findById(ticketId.toJavaUuid())?.let { ticketEntity ->
                    // Get user entity
                    UserEntity.findById(userId.toJavaUuid())?.let { userEntity ->
                        // Insert ticket into user links
                        userEntity.tickets = SizedCollection(userEntity.tickets + ticketEntity)
                    }
                }
            }
        } catch (ex: Exception) {
            AppLogger.i(ADD_TICKET_TAG, "Couldn't add ticket to user!")
            ex.printStackTrace()
        }
    }

    /** Gets all tickets for a user */
    fun getTickets(identifier: Uuid): List<Ticket> {
        val tickets = mutableListOf<Ticket>()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserEntity.findById(identifier.toJavaUuid())?.let { userEntity ->
                    tickets.addAll(
                        userEntity.tickets
                            .map { ticketEntity -> ticketEntity.toTicket() }
                    )

                    AppLogger.m(GET_TICKETS_TAG, SimpleError.SUCCESS)
                } ?: AppLogger.i(GET_TICKETS_TAG, "Could not find user!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_TICKETS_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return tickets
    }

    /** Adds a note to an item and linking it to that item */
    override fun addNote(identifier: Uuid, note: Note, storedImages: List<StoredImage>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to customer notes
                    UserEntity.findByIdAndUpdate(identifier.toJavaUuid()) { userEntity ->
                        userEntity.notes = SizedCollection(userEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleError.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, "User not found! $identifier")
                } ?: AppLogger.i(ADD_NOTE_TAG, "Note entity was null!")
            }

            return true
        } catch (ex: Exception) {
            AppLogger.i(ADD_NOTE_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return false
    }

    override fun getNotes(identifier: Uuid): Map<Note, List<StoredImage>> {
        val result = mutableMapOf<Note, List<StoredImage>>()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                UserEntity.findById(identifier.toJavaUuid())?.let { userEntity ->
                    userEntity.notes.forEach { noteEntity ->
                        result[noteEntity.toNote()] = noteEntity.images.map { it.toStoredImage() }
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
                UserTable
                    .join(
                        UserNoteTable, onColumn = UserTable.id, otherColumn = UserNoteTable.user,
                        joinType = JoinType.INNER,
                    )
                    .select(UserNoteTable.note eq note.id)
                    .limit(1)
                    .map { UserEntity.wrapRow(it) }
                    .firstOrNull()
                    ?.let { userEntity ->
                        userEntity.notes = SizedCollection(userEntity.notes.filter { it != note })
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
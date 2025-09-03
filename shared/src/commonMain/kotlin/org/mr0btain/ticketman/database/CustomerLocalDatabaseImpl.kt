package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.database.tables.customer.CustomerNoteTable
import org.mr0btain.ticketman.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.database.tables.customer.toCustomer
import org.mr0btain.ticketman.database.tables.image.toImage
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.toTicket
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class CustomerLocalDatabaseImpl(
    tag: String = "CustomerLocalDatabase",
    private val shouldLog: Boolean = false
) : LocalDatabase<Customer>(
    tag = tag,
    shouldLog = shouldLog
) {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"

    /** Gets all items from database */
    override fun getAll(): List<Customer>? {
        var result = listOf<Customer>()

        try {
            // Go into database and convert data to customer data item
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = CustomerEntity.all().map { item -> item.toCustomer()}

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
    override fun getById(identifier: Uuid): Customer? {
        var result: Customer? = null

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                result = CustomerEntity.findById(identifier.toJavaUuid())?.toCustomer()

                AppLogger.m(GET_BY_ID_TAG, SimpleError.SUCCESS)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(GET_BY_ID_TAG, SimpleError.FAIL)
            return null
        }

        return result
    }

    /** Adds all items to the database */
    override fun addAll(data: List<Customer>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                data.forEach { item ->
                    // Convert kotlin uuid to java for database
                    val identifier = item.identifier.toJavaUuid()

                    // Insert into database
                    CustomerEntity.new(identifier) {
                        name = item.name
                        address = item.address
                        contactInfo = item.contactInfo
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

                CustomerEntity.findById(identifier.toJavaUuid())?.delete()

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
    override fun add(data: Customer): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Convert kotlin uuid to java for database
                val identifier = data.identifier.toJavaUuid()

                // Insert into database
                CustomerEntity.new(identifier) {
                    name = data.name
                    address = data.address
                    contactInfo = data.contactInfo
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
    override fun update(data: Customer): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerEntity
                    .findById(data.identifier.toJavaUuid())
                    ?.apply {
                        name = data.name
                        address = data.address
                        contactInfo = data.contactInfo
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

    /** Adds a ticket to a customer, assuming that ticket and customer already exist in the database */
    fun addTicket(customerId: Uuid, ticketId: Uuid) {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Get ticket entity
                TicketEntity.findById(ticketId.toJavaUuid())?.let { ticketEntity ->
                    // Get customer entity
                    CustomerEntity.findById(customerId.toJavaUuid())?.let { customerEntity ->
                        // Insert ticket into customer links
                        customerEntity.tickets = SizedCollection(customerEntity.tickets + ticketEntity)
                    }
                }
            }
        } catch (ex: Exception) {
            AppLogger.i(ADD_TICKET_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }
    }

    /** Gets all tickets for a customer */
    fun getTickets(identifier: Uuid): List<Ticket> {
        val tickets = mutableListOf<Ticket>()

        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                CustomerEntity.findById(identifier.toJavaUuid())?.let { customerEntity ->
                    tickets.addAll(
                        customerEntity.tickets
                            .map { ticketEntity -> ticketEntity.toTicket() }
                    )

                    AppLogger.m(GET_TICKETS_TAG, SimpleError.SUCCESS)
                } ?: AppLogger.i(GET_TICKETS_TAG, "Could not find customer!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_TICKETS_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return tickets
    }

    /** Adds a note to an item and linking it to that item */
    override fun addNote(identifier: Uuid, note: Note, images: List<StoredImage>): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Insert the note into database and get its entity
                insertNote(note, images)?.let { noteEntity ->
                    // Add note to customer notes
                    CustomerEntity.findByIdAndUpdate(identifier.toJavaUuid()) { customerEntity ->
                        customerEntity.notes = SizedCollection(customerEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleError.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, "Customer not found! $identifier")
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

                CustomerEntity.findById(identifier.toJavaUuid())?.let { customerEntity ->
                    customerEntity.notes.forEach { noteEntity ->
                        result[noteEntity.toNote()] = noteEntity.images.map { it.toImage() }
                    }

                    AppLogger.m(GET_NOTES_TAG, SimpleError.SUCCESS)
                } ?: AppLogger.i(GET_NOTES_TAG, "Could not find customer!")
            }
        } catch (ex: Exception) {
            AppLogger.i(GET_NOTES_TAG, SimpleError.FAIL)
            ex.printStackTrace()
        }

        return result
    }

    /** Removes a note and its images from the database */
    override fun removeNote(identifier: Uuid): Boolean {
        try {
            transaction {
                // If we should log, setup logger
                if (shouldLog) addLogger(StdOutSqlLogger)

                // Find the note if it exists
                val note = NoteEntity.findById(identifier.toJavaUuid()) ?: return@transaction

                // No clue if this actually works or not??
                CustomerTable
                    .join(
                        CustomerNoteTable, onColumn = CustomerTable.id, otherColumn = CustomerNoteTable.customer,
                        joinType = JoinType.INNER,
                    )
                    .select(CustomerNoteTable.note eq note.id)
                    .limit(1)
                    .map { CustomerEntity.wrapRow(it) }
                    .firstOrNull()
                    ?.let { customerEntity ->
                        customerEntity.notes = SizedCollection(customerEntity.notes.filter { it != note })
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
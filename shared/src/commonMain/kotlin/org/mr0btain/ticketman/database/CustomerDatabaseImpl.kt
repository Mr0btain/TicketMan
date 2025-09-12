package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.database.tables.customer.toCustomer
import org.mr0btain.ticketman.database.tables.image.toStoredImage
import org.mr0btain.ticketman.database.tables.note.toNote
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.database.tables.ticket.toTicket
import org.mr0btain.ticketman.error.CustomerError
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Used for all Customer data actions in database
 */
@Suppress("PrivatePropertyName")
@OptIn(ExperimentalUuidApi::class)
open class CustomerDatabaseImpl() : Database<Customer>("CustomerDatabaseImpl") {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val REMOVE_TICKET_TAG = "$tag:RemoveTicket"

    override fun all(): List<Customer>? {
        var result: List<Customer>? = null

        appLogTryCatch(GET_ALL_TAG) {
            transaction {
                result = CustomerEntity.all().map { item -> item.toCustomer() }
            }

            if (result != null)
                AppLogger.m(GET_ALL_TAG, SimpleResult.SUCCESS)
            else AppLogger.i(GET_ALL_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun getById(identifier: Uuid): Customer? {
        var result: Customer? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                CustomerEntity.findById(identifier.toJavaUuid())?.let { customerEntity ->
                    result = customerEntity.toCustomer()
                    AppLogger.m(GET_BY_ID_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(GET_BY_ID_TAG, CustomerError.NOT_FOUND)
            }
        }

        return result
    }

    override fun getPagedItems(offset: Long, pageSize: Int): List<Customer>? {
        var result: List<Customer>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = CustomerEntity
                    .all()
                    .orderBy(CustomerTable.name to SortOrder.ASC, CustomerTable.time to SortOrder.DESC)
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toCustomer() }
            }

            if (result != null)
                AppLogger.m(GET_PAGED_ITEMS_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_PAGED_ITEMS_TAG, SimpleResult.FAIL)
        }

        return result
    }

    override fun count(): Long = CustomerEntity.count()

    override fun addAll(data: List<Customer>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    CustomerEntity.new(item.identifier.toJavaUuid()) {
                        name = item.name
                        address = item.address
                        contactInfo = item.contactInfo
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
                CustomerEntity.findById(identifier.toJavaUuid())?.let { customerEntity ->
                    customerEntity.delete()

                    AppLogger.m(REMOVE_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(REMOVE_TAG, CustomerError.NOT_FOUND)
            }
        }
    }

    override fun add(data: Customer) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                CustomerEntity.new(data.identifier.toJavaUuid()) {
                    name = data.name
                    address = data.address
                    contactInfo = data.contactInfo
                    time = data.time
                }

                AppLogger.m(ADD_TAG, SimpleResult.SUCCESS)
            }
        }
    }

    override fun update(data: Customer) {
        appLogTryCatch(UPDATE_TAG) {
            transaction {
                CustomerEntity
                    .findById(data.identifier.toJavaUuid())
                    ?.apply {
                        name = data.name
                        address = data.address
                        contactInfo = data.contactInfo
                        time = data.time

                        AppLogger.m(UPDATE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(UPDATE_TAG, CustomerError.NOT_FOUND)
            }
        }
    }

    override fun addNote(identifier: Uuid, note: Note, storedImages: List<StoredImage>) {
        appLogTryCatch(ADD_NOTE_TAG) {
            transaction {
                // Insert the note into database and get its entity
                insertNote(note, storedImages)?.let { noteEntity ->
                    // Add note to customer notes
                    CustomerEntity.findByIdAndUpdate(identifier.toJavaUuid()) { customerEntity ->
                        customerEntity.notes = SizedCollection(customerEntity.notes + noteEntity)

                        AppLogger.m(ADD_NOTE_TAG, SimpleResult.SUCCESS)
                    } ?: AppLogger.i(ADD_NOTE_TAG, CustomerError.NOT_FOUND)
                }
            }
        }
    }

    override fun getNotes(identifier: Uuid): Map<Note, List<StoredImage>>? {
        var result: Map<Note, List<StoredImage>>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                CustomerEntity.findById(identifier.toJavaUuid())?.let { customerEntity ->
                    result = customerEntity.notes.associate { noteEntity ->
                        noteEntity.toNote() to noteEntity.images.map { it.toStoredImage() }
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, CustomerError.NOT_FOUND)
            }

            if (result != null)
                AppLogger.m(GET_NOTES_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_NOTES_TAG, SimpleResult.FAIL)
        }

        return result
    }

    /**
     * Adds a ticket to a customer, assuming that ticket and customer already exist in the database
     * @param customerId Identifier of customer to link ticket to
     * @param ticketId Identifier of ticket to link to customer
     */
    fun addTicket(customerId: Uuid, ticketId: Uuid) {
        appLogTryCatch(ADD_TICKET_TAG) {
            transaction {
                // Get ticket entity
                TicketEntity.findById(ticketId.toJavaUuid())?.let { ticketEntity ->
                    // Get customer entity
                    CustomerEntity.findById(customerId.toJavaUuid())?.let { customerEntity ->
                        // Insert ticket into customer links
                        customerEntity.tickets = SizedCollection(customerEntity.tickets + ticketEntity)
                    } ?: AppLogger.i(ADD_TICKET_TAG, CustomerError.NOT_FOUND)
                } ?: AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND)
            }
        }
    }

    /**
     * Removes a ticket from a customer. NOTE: This DOES NOT delete the ticket itself
     * @param customerId Identifier of customer to unlink ticket from
     * @param ticketId Identifier of ticket to unlink from customer
     */
    fun removeTicket(customerId: Uuid, ticketId: Uuid) {
        appLogTryCatch(REMOVE_TICKET_TAG) {
            transaction {
                // Get customer entity
                CustomerEntity.findById(customerId.toJavaUuid())?.let { customerEntity ->
                    // Remove ticket
                    customerEntity.tickets = SizedCollection(
                        customerEntity.tickets
                            .filter { it.id.value.toKotlinUuid() != ticketId }
                    )

                    AppLogger.m(REMOVE_TICKET_TAG, SimpleResult.SUCCESS)
                } ?: AppLogger.i(REMOVE_TICKET_TAG, CustomerError.NOT_FOUND)
            }
        }
    }

    /**
     * Gets all tickets for a customer
     * @param identifier Identifier of customer to get tickets for
     */
    fun getTickets(identifier: Uuid): List<Ticket>? {
        var tickets: List<Ticket>? = null

        appLogTryCatch(GET_TICKETS_TAG) {
            transaction {
                CustomerEntity.findById(identifier.toJavaUuid())?.let { customerEntity ->
                    tickets = customerEntity.tickets
                        .map { ticketEntity -> ticketEntity.toTicket() }
                } ?: AppLogger.i(GET_TICKETS_TAG, CustomerError.NOT_FOUND)
            }

            if (tickets != null)
                AppLogger.m(GET_TICKETS_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.i(GET_TICKETS_TAG, SimpleResult.FAIL)
        }

        return tickets
    }
}
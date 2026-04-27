package org.mr0btain.ticketman.database

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.error.CustomerError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import java.util.*

/**
 * Used for all Customer data actions in database
 */
@Suppress("PrivatePropertyName")
open class CustomerDatabaseImpl() : Database<Customer>("CustomerDatabaseImpl") {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val REMOVE_TICKET_TAG = "$tag:RemoveTicket"
    private val UPDATE_NAME_TAG = "$tag:UpdateName"
    private val UPDATE_PHONE_TAG = "$tag:UpdateName"
    private val UPDATE_EMAIL_TAG = "$tag:UpdateName"

    override fun all(): List<Customer>? {
        var result: List<Customer>? = null

        appLogTryCatch(GET_ALL_TAG) {
            transaction {
                result = CustomerEntity.all().map { item -> item.toCustomer() }
            }

            // Return boolean
            result != null
        }

        return result
    }

    override fun getById(identifier: UUID): Customer? {
        var result: Customer? = null

        appLogTryCatch(GET_BY_ID_TAG) {
            transaction {
                CustomerEntity.findById(identifier)?.let { customerEntity ->
                    result = customerEntity.toCustomer()
                } ?: AppLogger.i(GET_BY_ID_TAG, CustomerError.NOT_FOUND)

                result != null
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
                    .orderBy(CustomerTable.name to SortOrder.ASC, CustomerTable.id to SortOrder.DESC)
                    .offset(offset)
                    .limit(pageSize)
                    .map { it.toCustomer() }
            }
            println("Customer Paged items size: ${result?.size}")
            result != null
        }

        return result
    }

    override fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = CustomerEntity.count()
                true
            }
        }
        return count
    }

    override fun addAll(data: List<Customer>) {
        appLogTryCatch(ADD_ALL_TAG) {
            transaction {
                data.forEach { item ->
                    // Insert into database
                    CustomerEntity.new(item.identifier) {
                        name = item.name.lowercase()
                        address = item.address
                        email = item.email.lowercase()
                        phone = item.phone
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
                CustomerEntity.findById(identifier)?.let { customerEntity ->
                    customerEntity.delete()
                    result = true
                } ?: AppLogger.m(REMOVE_TAG, CustomerError.NOT_FOUND)
            }

            result
        }
    }

    override fun search(query: String, limit: Int): List<SearchResult>? {
        return appLogTryCatch<List<SearchResult>?>(SEARCH_TAG) {
            transaction {
                val pattern = "${query.lowercase()}%"

                Pair(
                    CustomerEntity
                        .find {
                            (CustomerTable.name.lowerCase() like pattern) or
                                    (CustomerTable.id.castTo(VarCharColumnType()) like pattern) or
                                    (CustomerTable.email.lowerCase() like pattern)
                        }
                        .orderBy(
                            CustomerTable.name to SortOrder.ASC,
                            CustomerTable.email to SortOrder.ASC,
                            CustomerTable.id to SortOrder.ASC
                        )
                        .limit(limit)
                        .map { customerEntity ->
                            SearchResult(
                                identifier = customerEntity.id.value,
                                text = when {
                                    customerEntity.name.startsWith(query, true) -> customerEntity.name
                                    customerEntity.email.startsWith(query, true) -> customerEntity.email
                                    customerEntity.id.value.toString()
                                        .startsWith(query, true) -> customerEntity.id.value.toString()

                                    else -> customerEntity.name
                                }
                            )
                        },
                    true
                )
            }
        }
    }

    override fun add(data: Customer) {
        appLogTryCatch(ADD_TAG) {
            transaction {
                // Insert into database
                CustomerEntity.new(data.identifier) {
                    name = data.name.lowercase()
                    address = data.address
                    email = data.email.lowercase()
                    phone = data.phone
                }

                true
            }
        }
    }

    override fun update(data: Customer) {
        appLogTryCatch(UPDATE_TAG) {
            transaction {
                var result = false

                CustomerEntity
                    .findById(data.identifier)
                    ?.apply {
                        name = data.name.lowercase()
                        address = data.address
                        email = data.email.lowercase()
                        phone = data.phone

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, CustomerError.NOT_FOUND)

                result
            }
        }
    }

    fun updateName(identifier: UUID, newName: String) {
        appLogTryCatch(UPDATE_NAME_TAG) {
            transaction {
                var result = false

                CustomerEntity
                    .findById(identifier)
                    ?.apply {
                        name = newName.lowercase()
                        result = true
                    } ?: AppLogger.i(UPDATE_NAME_TAG, CustomerError.NOT_FOUND)

                result
            }
        }
    }

    fun updatePhone(identifier: UUID, newPhone: String) {
        appLogTryCatch(UPDATE_PHONE_TAG) {
            transaction {
                var result = false

                CustomerEntity
                    .findById(identifier)
                    ?.apply {
                        phone = newPhone
                        result = true
                    } ?: AppLogger.i(UPDATE_PHONE_TAG, CustomerError.NOT_FOUND)

                result
            }
        }
    }

    fun updateEmail(identifier: UUID, newEmail: String) {
        appLogTryCatch(UPDATE_EMAIL_TAG) {
            transaction {
                var result = false

                CustomerEntity
                    .findById(identifier)
                    ?.apply {
                        email = newEmail.lowercase()
                        result = true
                    } ?: AppLogger.i(UPDATE_EMAIL_TAG, CustomerError.NOT_FOUND)

                result
            }
        }
    }

    override fun addNote(identifier: UUID, note: Note) {
        appLogTryCatch(ADD_NOTE_TAG) {
            var result = false

            transaction {
                // Insert the note into database and get its entity
                insertNote(note)?.let { noteEntity ->
                    // Add note to customer notes
                    CustomerEntity.findByIdAndUpdate(identifier) { customerEntity ->
                        customerEntity.notes = SizedCollection(customerEntity.notes + noteEntity)
                        result = true
                    } ?: AppLogger.i(ADD_NOTE_TAG, CustomerError.NOT_FOUND)
                }
            }

            result
        }
    }

    override fun getNotes(identifier: UUID): List<Note>? {
        var result: List<Note>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                CustomerEntity.findById(identifier)?.let { customerEntity ->
                    result = customerEntity.notes.map { noteEntity ->
                        noteEntity.toNote()
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, CustomerError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    /**
     * Adds a ticket to a customer, assuming that ticket and customer already exist in the database
     * @param customerId Identifier of customer to link ticket to
     * @param ticketId Identifier of ticket to link to customer
     */
    fun addTicket(customerId: UUID, ticketId: UUID) {
        appLogTryCatch(ADD_TICKET_TAG) {
            var result = false

            transaction {
                // Get ticket entity
                TicketEntity.findById(ticketId)?.let { ticketEntity ->
                    // Get customer entity
                    CustomerEntity.findById(customerId)?.let { customerEntity ->
                        // Insert ticket into customer links
                        customerEntity.tickets = SizedCollection(customerEntity.tickets + ticketEntity)

                        result = true
                    } ?: AppLogger.i(ADD_TICKET_TAG, CustomerError.NOT_FOUND)
                } ?: AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    /**
     * Removes a ticket from a customer. NOTE: This DOES NOT delete the ticket itself
     * @param customerId Identifier of customer to unlink ticket from
     * @param ticketId Identifier of ticket to unlink from customer
     */
    fun removeTicket(customerId: UUID, ticketId: UUID) {
        appLogTryCatch(REMOVE_TICKET_TAG) {
            var result = false

            transaction {
                // Get customer entity
                CustomerEntity.findById(customerId)?.let { customerEntity ->
                    // Remove ticket
                    customerEntity.tickets = SizedCollection(
                        customerEntity.tickets
                            .filter { it.id.value != ticketId }
                    )

                    result = true
                } ?: AppLogger.i(REMOVE_TICKET_TAG, CustomerError.NOT_FOUND)
            }

            result
        }
    }

    /**
     * Gets all tickets for a customer
     * @param identifier Identifier of customer to get tickets for
     */
    fun getTickets(identifier: UUID): List<Ticket>? {
        var tickets: List<Ticket>? = null

        appLogTryCatch(GET_TICKETS_TAG) {
            transaction {
                CustomerEntity.findById(identifier)?.let { customerEntity ->
                    tickets = customerEntity.tickets
                        .map { ticketEntity -> ticketEntity.toTicket() }
                } ?: AppLogger.i(GET_TICKETS_TAG, CustomerError.NOT_FOUND)
            }

            tickets != null
        }

        return tickets
    }
}
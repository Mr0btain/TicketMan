package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable.address
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable.email
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable.name
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable.phone
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.CustomerError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Used for all Customer data actions in database
 */
@OptIn(ExperimentalUuidApi::class)
open class CustomerDatabaseImpl() : Database<Customer>("CustomerDatabaseImpl") {
    private val GET_TICKETS_TAG = "$tag:GetTickets"
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val REMOVE_TICKET_TAG = "$tag:RemoveTicket"
    private val UPDATE_NAME_TAG = "$tag:UpdateName"
    private val UPDATE_PHONE_TAG = "$tag:UpdateName"
    private val UPDATE_EMAIL_TAG = "$tag:UpdateName"

    override fun getById(identifier: Uuid): Customer? {
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

    override fun getPagedItems(request: PagedRequest): List<Customer>? {
        var result: List<Customer>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = CustomerEntity
                    .all()
                    .orderBy(name to SortOrder.ASC, CustomerTable.id to SortOrder.DESC)
                    .offset(request.offset)
                    .limit(request.itemsPerPage)
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

    override fun remove(identifier: Uuid) {
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

    override fun search(query: String, limit: Int): List<SearchResult>? =
        searchCustomers(query, limit)?.map { customer ->
            SearchResult(
                identifier = customer.identifier,
                text = listOf(customer.cleanName(), customer.phone).filter { it.isNotBlank() }.joinToString(" - ")
            )
        }

    /**
     * Customers matching any part of their name ("jane doe" works), the start of their email or id,
     * or their phone number ignoring formatting
     */
    fun searchCustomers(query: String, limit: Int): List<Customer>? {
        return appLogTryCatch<List<Customer>?>(SEARCH_TAG) {
            transaction {
                val text = query.trim().lowercase()
                val digits = text.filter { it.isDigit() }

                // Names are stored as first:last, so spaces in the query can match the divider
                val namePattern = "%${text.replace(' ', '%')}%"

                // Phone numbers are stored however they were typed, compare digits only
                val phoneDigits = CustomFunction(
                    "REGEXP_REPLACE",
                    TextColumnType(),
                    CustomerTable.phone,
                    stringLiteral("[^0-9]"),
                    stringLiteral("")
                )

                val phoneMatch: Op<Boolean> = if (digits.length >= MIN_PHONE_DIGITS) phoneDigits like "%$digits%" else Op.FALSE

                Pair(
                    CustomerEntity
                        .find {
                            (CustomerTable.name.lowerCase() like namePattern) or
                                    (CustomerTable.id.castTo(VarCharColumnType()) like "$text%") or
                                    (CustomerTable.email.lowerCase() like "$text%") or
                                    phoneMatch
                        }
                        .orderBy(
                            CustomerTable.name to SortOrder.ASC,
                            CustomerTable.email to SortOrder.ASC,
                            CustomerTable.id to SortOrder.ASC
                        )
                        .limit(limit)
                        .map { customerEntity -> customerEntity.toCustomer() },
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

    fun updateName(identifier: Uuid, newName: String) {
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

    fun updatePhone(identifier: Uuid, newPhone: String) {
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

    fun updateEmail(identifier: Uuid, newEmail: String) {
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

    override fun addNote(identifier: Uuid, note: Note) {
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

    override fun getNotes(identifier: Uuid): List<Note>? {
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
    fun addTicket(customerId: Uuid, ticketId: Uuid) {
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
    fun removeTicket(customerId: Uuid, ticketId: Uuid) {
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
    fun getTickets(identifier: Uuid): List<Ticket>? {
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

    companion object {
        // Fewer digits than this is too short to be worth matching against phone numbers
        private const val MIN_PHONE_DIGITS = 3
    }
}

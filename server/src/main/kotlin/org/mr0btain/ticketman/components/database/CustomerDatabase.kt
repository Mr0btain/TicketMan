package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.select
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
import org.mr0btain.ticketman.error.CustomerError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.components.database.tables.customer.CustomerNoteTable

/**
 * Used for all Customer data actions in database
 */
@OptIn(ExperimentalUuidApi::class)
open class CustomerDatabase() : Database<Customer>("CustomerDatabase") {
    private val ADD_TICKET_TAG = "$tag:AddTicket"
    private val MIGRATE_NAMES_TAG = "$tag:MigrateNames"
    private val CONTACTS_TAG = "$tag:Contacts"

    override fun getById(identifier: Uuid): Customer? =
        dbQuery(GET_BY_ID_TAG) {
            CustomerEntity.findById(identifier)?.toCustomer()
                ?: null.also { AppLogger.i(GET_BY_ID_TAG, CustomerError.NOT_FOUND) }
        }

    override fun getPagedItems(request: PagedRequest): List<Customer>? =
        dbQuery(GET_PAGED_ITEMS_TAG) {
            CustomerEntity
                .all()
                .orderBy(name to SortOrder.ASC, CustomerTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toCustomer() }
        }

    override fun count(): Long =
        dbQuery(COUNT_TAG) { CustomerEntity.count() } ?: 0L

    override fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) {
            val customerEntity = CustomerEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_TAG, CustomerError.NOT_FOUND) }

            val notes = customerEntity.notes.toList()
            customerEntity.delete()
            deleteNotes(notes)
            true
        }

    /** Rewrites names older versions stored as `first:last` or lowercased, see [legacyName]. Run once */
    fun migrateLegacyNames(): Boolean =
        dbWrite(MIGRATE_NAMES_TAG) {
            CustomerEntity.all().filter { isLegacyName(it.name) }.forEach { it.name = legacyName(it.name) }
            true
        }

    override fun search(query: String, limit: Int): List<SearchResult>? =
        searchCustomers(query, limit)?.map { customer ->
            SearchResult(
                identifier = customer.identifier,
                text = listOf(customer.name, customer.phone).filter { it.isNotBlank() }.joinToString(" - ")
            )
        }

    /**
     * Customers matching any part of their name ("jane doe" works), the start of their email or id,
     * or their phone number ignoring formatting
     */
    fun searchCustomers(query: String, limit: Int): List<Customer>? =
        dbQuery(SEARCH_TAG) {
            val text = query.trim().lowercase()
            val digits = text.filter { it.isDigit() }

            // Spaces in the query match anything between words, so "jane doe" finds "Jane A. Doe"
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

            CustomerEntity
                .find {
                    (CustomerTable.name.lowerCase() like namePattern) or
                            (CustomerTable.id.castTo(VarCharColumnType()) like "$text%") or
                            (CustomerTable.email.lowerCase() like "$text%") or
                            phoneMatch
                }
                .orderBy(
                    CustomerTable.name.lowerCase() to SortOrder.ASC,
                    CustomerTable.email to SortOrder.ASC,
                    CustomerTable.id to SortOrder.ASC
                )
                .limit(limit)
                .map { customerEntity -> customerEntity.toCustomer() }
        }

    /**
     * Every customer's name, phone and email, for an import to find ones already there. Null when the read failed
     */
    fun contacts(): List<Triple<String, String, String>>? =
        dbQuery(CONTACTS_TAG) {
            CustomerTable
                .select(CustomerTable.name, CustomerTable.phone, CustomerTable.email)
                .map { Triple(it[CustomerTable.name], it[CustomerTable.phone], it[CustomerTable.email]) }
        }

    override fun add(data: Customer): Boolean =
        dbWrite(ADD_TAG) {
            insertCustomer(data)
            true
        }

    // Must be called inside a transaction
    private fun insertCustomer(data: Customer) {
        CustomerEntity.new(data.identifier) {
            name = data.name.trim()
            address = data.address
            email = data.email.lowercase()
            phone = data.phone
            notifyEmail = data.notifyEmail
            notifySms = data.notifySms
        }
    }

    override fun update(data: Customer): Boolean =
        dbWrite(UPDATE_TAG) {
            val customerEntity = CustomerEntity.findById(data.identifier)
                ?: return@dbWrite false.also { AppLogger.i(UPDATE_TAG, CustomerError.NOT_FOUND) }

            customerEntity.apply {
                name = data.name.trim()
                address = data.address
                email = data.email.lowercase()
                phone = data.phone
                notifyEmail = data.notifyEmail
                notifySms = data.notifySms
            }

            true
        }

    override val noteLinkColumn: Column<EntityID<Uuid>> = CustomerNoteTable.note

    override fun addNote(identifier: Uuid, note: Note): Boolean =
        attachNote(note, CustomerError.NOT_FOUND, { CustomerEntity.findById(identifier) }) { customerEntity, noteEntity ->
            customerEntity.notes = SizedCollection(customerEntity.notes + noteEntity)
        }

    override fun getNotes(identifier: Uuid): List<Note>? =
        dbQuery(GET_NOTES_TAG) {
            CustomerEntity.findById(identifier)?.notes?.map { noteEntity -> noteEntity.toNote() }
                ?: null.also { AppLogger.i(GET_NOTES_TAG, CustomerError.NOT_FOUND) }
        }

    /**
     * Adds a ticket to a customer, assuming that ticket and customer already exist in the database
     * @param customerId Identifier of customer to link ticket to
     * @param ticketId Identifier of ticket to link to customer
     * @return Whether both were found
     */
    fun addTicket(customerId: Uuid, ticketId: Uuid): Boolean =
        dbWrite(ADD_TICKET_TAG) {
            val ticketEntity = TicketEntity.findById(ticketId)
                ?: return@dbWrite false.also { AppLogger.i(ADD_TICKET_TAG, TicketError.NOT_FOUND) }
            val customerEntity = CustomerEntity.findById(customerId)
                ?: return@dbWrite false.also { AppLogger.i(ADD_TICKET_TAG, CustomerError.NOT_FOUND) }

            customerEntity.tickets = SizedCollection(customerEntity.tickets + ticketEntity)
            true
        }

    companion object {
        // Fewer digits than this is too short to be worth matching against phone numbers
        private const val MIN_PHONE_DIGITS = 3
    }
}

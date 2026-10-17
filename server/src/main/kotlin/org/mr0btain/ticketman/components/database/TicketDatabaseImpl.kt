package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketSignatureTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketStatusHistoryTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.user.UserEntity
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.components.database.tables.user.UserTicketTable
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.error.CustomerError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.UserError
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.logging.AppLogger
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @param currentYear Year new ticket numbers are made in, swappable so tests can cross a year
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class TicketDatabaseImpl(
    private val currentYear: () -> Int = { Year.now().value }
) : Database<Ticket>("TicketLocalDatabase") {
    private val BACKFILL_TAG = "$tag:BackfillNumbers"
    private val GET_BY_NUMBER_TAG = "$tag:GetByNumber"
    private val STATUS_HISTORY_TAG = "$tag:StatusHistory"
    private val RECORD_STATUS_TAG = "$tag:RecordStatus"
    private val SET_CUSTOMER_TAG = "$tag:SetCustomer"
    private val SET_USER_TAG = "$tag:SetUser"
    private val SUMMARIZE_TAG = "$tag:Summarize"
    private val SIGNATURE_TAG = "$tag:Signature"
    private val GET_CUSTOMER_TAG = "$tag:GetCustomer"
    private val GET_USER_TAG = "$tag:GetUser"
    private val UPDATE_DEVICE_TAG = "$tag:UpdateDevice"
    private val UPDATE_STATUS_TAG = "$tag:UpdateStatus"
    private val UPDATE_PRIORITY_TAG = "$tag:UpdatePriority"

    override fun getById(identifier: Uuid): Ticket? {
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

    override fun getPagedItems(request: PagedRequest): List<Ticket>? {
        var result: List<Ticket>? = null

        appLogTryCatch(GET_PAGED_ITEMS_TAG) {
            transaction {
                result = TicketEntity.all()
                    .orderBy(TicketTable.id to SortOrder.DESC) // or ASC
                    .offset(request.offset)
                    .limit(request.itemsPerPage)
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
                synchronized(NUMBER_LOCK) {
                    data.forEach { item -> insertTicket(item) }
                }

                true
            }
        }
    }

    override fun remove(identifier: Uuid) {
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

    override fun search(query: String, limit: Int): List<SearchResult>? =
        searchTickets(query, limit)?.map { ticket ->
            SearchResult(
                identifier = ticket.identifier,
                text = "${ticket.label} ${ticket.device}".trim()
            )
        }

    /**
     * Tickets matching a number ("26-42" exact, "42" any year), part of the id, device make/model/serial or problem.
     * Newest number first
     */
    fun searchTickets(query: String, limit: Int): List<Ticket>? {
        return appLogTryCatch<List<Ticket>?>(SEARCH_TAG) {
            transaction {
                val text = query.trim().lowercase()
                val contains = "%$text%"

                // "26-42" is an exact number, plain digits match a sequence in any year
                val numberMatch: Op<Boolean> = TicketNumber.parse(text)
                    ?.let { (TicketTable.numberYear eq it.year) and (TicketTable.numberSequence eq it.sequence) }
                    ?: text.toLongOrNull()?.let { TicketTable.numberSequence eq it }
                    ?: Op.FALSE

                Pair(TicketEntity
                    .find {
                        numberMatch or
                            (TicketTable.id.castTo(VarCharColumnType()) like "$text%") or
                            (TicketTable.device.castTo(VarCharColumnType()).lowerCase() like contains) or
                            (TicketTable.problem.lowerCase() like contains)
                    }
                    .orderBy(TicketTable.numberYear to SortOrder.DESC, TicketTable.numberSequence to SortOrder.DESC)
                    .limit(limit)
                    .map { ticketEntity -> ticketEntity.toTicket() },
                    true
                    )
            }
        }
    }

    override fun add(data: Ticket) {
        appLogTryCatch(ADD_TAG) {
            // One at a time so two counters can't be handed the same number
            synchronized(NUMBER_LOCK) {
                transaction { insertTicket(data) }
            }

            true
        }
    }

    // Must be called inside a transaction while holding NUMBER_LOCK
    private fun insertTicket(data: Ticket) {
        val year = currentYear()

        TicketEntity.new(data.identifier) {
            device = data.device
            status = data.status
            priority = data.priority
            numberYear = year
            numberSequence = nextSequence(year)
            problem = data.problem
            condition = data.condition
            accessories = data.accessories
            estimateCents = data.estimateCents
            promisedAt = data.promisedAt
        }
    }

    private fun nextSequence(year: Int): Long {
        val max = TicketTable.numberSequence.max()

        return (TicketTable
            .select(max)
            .where { TicketTable.numberYear eq year }
            .singleOrNull()
            ?.get(max) ?: 0L) + 1
    }

    /**
     * Numbers tickets made before numbering existed, oldest first, in the year they were made
     */
    fun backfillNumbers() {
        appLogTryCatch(BACKFILL_TAG) {
            synchronized(NUMBER_LOCK) {
                transaction {
                    TicketEntity
                        .find { TicketTable.numberSequence.isNull() }
                        .orderBy(TicketTable.id to SortOrder.ASC)
                        .forEach { ticketEntity ->
                            val year = Instant.ofEpochMilli(ticketEntity.id.value.timestampMillis())
                                .atZone(ZoneId.systemDefault())
                                .year

                            ticketEntity.numberYear = year
                            ticketEntity.numberSequence = nextSequence(year)
                            ticketEntity.flush()
                        }
                }
            }

            true
        }
    }

    /** Saves the check in signature, replacing any the ticket had */
    fun setSignature(identifier: Uuid, signature: TicketSignature): Boolean =
        appLogTryCatch<Boolean>(SIGNATURE_TAG) {
            transaction {
                TicketSignatureTable.deleteWhere { TicketSignatureTable.ticket eq identifier }
                TicketSignatureTable.insert { row ->
                    row[TicketSignatureTable.ticket] = identifier
                    row[TicketSignatureTable.signature] = signature
                }

                Pair(true, true)
            }
        } ?: false

    fun getSignature(identifier: Uuid): TicketSignature? =
        appLogTryCatch<TicketSignature?>(SIGNATURE_TAG) {
            transaction {
                val signature = TicketSignatureTable
                    .selectAll()
                    .where { TicketSignatureTable.ticket eq identifier }
                    .firstOrNull()
                    ?.get(TicketSignatureTable.signature)

                Pair(signature, true)
            }
        }

    /** Closes a ticket as picked up at [time] */
    fun markPickedUp(identifier: Uuid, time: Long): Boolean {
        return appLogTryCatch<Boolean>(UPDATE_STATUS_TAG) {
            transaction {
                val ticket = TicketEntity.findById(identifier)

                ticket?.apply {
                    status = TicketStatus(TicketStatusType.PICKED_UP, time)
                    pickedUpAt = time
                }

                Pair(ticket != null, ticket != null)
            }
        } ?: false
    }

    fun getByNumber(number: TicketNumber): Ticket? {
        return appLogTryCatch<Ticket?>(GET_BY_NUMBER_TAG) {
            transaction {
                val ticket = TicketEntity
                    .find { (TicketTable.numberYear eq number.year) and (TicketTable.numberSequence eq number.sequence) }
                    .firstOrNull()
                    ?.toTicket()

                Pair(ticket, ticket != null)
            }
        }
    }

    /**
     * Adds an entry to a ticket's status history
     * @param changedBy Username of whoever made the change
     */
    fun recordStatusChange(identifier: Uuid, status: TicketStatusType, changedBy: String) {
        appLogTryCatch(RECORD_STATUS_TAG) {
            transaction {
                TicketStatusHistoryTable.insert { row ->
                    row[TicketStatusHistoryTable.id] = Uuid.generateV7()
                    row[TicketStatusHistoryTable.ticket] = identifier
                    row[TicketStatusHistoryTable.status] = status.name
                    row[TicketStatusHistoryTable.time] = Clock.System.now().toEpochMilliseconds()
                    row[TicketStatusHistoryTable.changedBy] = changedBy
                }
            }

            true
        }
    }

    /** Status changes oldest first */
    fun getStatusHistory(identifier: Uuid): List<TicketStatusChange>? {
        return appLogTryCatch<List<TicketStatusChange>?>(STATUS_HISTORY_TAG) {
            transaction {
                val history = TicketStatusHistoryTable
                    .selectAll()
                    .where { TicketStatusHistoryTable.ticket eq identifier }
                    .orderBy(TicketStatusHistoryTable.time to SortOrder.ASC, TicketStatusHistoryTable.id to SortOrder.ASC)
                    .map { row ->
                        TicketStatusChange(
                            status = TicketStatusType.valueOf(row[TicketStatusHistoryTable.status]),
                            time = row[TicketStatusHistoryTable.time],
                            changedBy = row[TicketStatusHistoryTable.changedBy]
                        )
                    }

                Pair(history, true)
            }
        }
    }

    override fun update(data: Ticket) {
        appLogTryCatch(UPDATE_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(data.identifier) { item ->
                        // Number and pick up time are owned by the server, so they aren't taken from the client
                        item.device = data.device
                        item.status = data.status
                        item.priority = data.priority
                        item.problem = data.problem
                        item.condition = data.condition
                        item.accessories = data.accessories
                        item.estimateCents = data.estimateCents
                        item.promisedAt = data.promisedAt

                        result = true
                    } ?: AppLogger.i(UPDATE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    fun updateDevice(identifier: Uuid, device: Device) {
        appLogTryCatch(UPDATE_DEVICE_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(identifier) { item ->
                        item.device = device

                        result = true
                    } ?: AppLogger.i(UPDATE_DEVICE_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    fun updateStatus(identifier: Uuid, status: TicketStatus) {
        appLogTryCatch(UPDATE_STATUS_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(identifier) { item ->
                        item.status = status

                        result = true
                    } ?: AppLogger.i(UPDATE_STATUS_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    fun updatePriority(identifier: Uuid, priority: TicketPriority) {
        appLogTryCatch(UPDATE_PRIORITY_TAG) {
            var result = false

            transaction {
                TicketEntity
                    .findByIdAndUpdate(identifier) { item ->
                        item.priority = priority

                        result = true
                    } ?: AppLogger.i(UPDATE_PRIORITY_TAG, TicketError.NOT_FOUND)
            }

            result
        }
    }

    override fun addNote(identifier: Uuid, note: Note) {
        appLogTryCatch(ADD_NOTE_TAG) {
            var result = false

            transaction {
                // Insert the note into database and get its entity
                insertNote(note)?.let { noteEntity ->
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

    override fun getNotes(identifier: Uuid): List<Note>? {
        var result: List<Note>? = null

        appLogTryCatch(GET_NOTES_TAG) {
            transaction {
                TicketEntity.findById(identifier)?.let { ticketEntity ->
                    result = ticketEntity.notes.map { noteEntity ->
                        noteEntity.toNote()
                    }
                } ?: AppLogger.i(GET_NOTES_TAG, TicketError.NOT_FOUND)
            }

            result != null
        }

        return result
    }

    /**
     * Links a ticket to a customer, replacing any customer it had
     * @return Whether both exist
     */
    fun setCustomer(ticketId: Uuid, customerId: Uuid): Boolean {
        return appLogTryCatch<Boolean>(SET_CUSTOMER_TAG) {
            transaction {
                val ticket = TicketEntity.findById(ticketId)
                val customer = CustomerEntity.findById(customerId)

                when {
                    ticket == null -> AppLogger.i(SET_CUSTOMER_TAG, TicketError.NOT_FOUND)
                    customer == null -> AppLogger.i(SET_CUSTOMER_TAG, CustomerError.NOT_FOUND)
                    else -> {
                        CustomerTicketTable.deleteWhere { CustomerTicketTable.ticket eq ticketId }
                        CustomerTicketTable.insert { row ->
                            row[CustomerTicketTable.customer] = customerId
                            row[CustomerTicketTable.ticket] = ticketId
                        }
                    }
                }

                val linked = ticket != null && customer != null
                Pair(linked, linked)
            }
        } ?: false
    }

    /**
     * Assigns a ticket to a tech, replacing whoever had it
     * @return Whether both exist
     */
    fun setUser(ticketId: Uuid, userId: Uuid): Boolean {
        return appLogTryCatch<Boolean>(SET_USER_TAG) {
            transaction {
                val ticket = TicketEntity.findById(ticketId)
                val user = UserEntity.findById(userId)

                when {
                    ticket == null -> AppLogger.i(SET_USER_TAG, TicketError.NOT_FOUND)
                    user == null -> AppLogger.i(SET_USER_TAG, UserError.NOT_FOUND)
                    else -> {
                        UserTicketTable.deleteWhere { UserTicketTable.ticket eq ticketId }
                        UserTicketTable.insert { row ->
                            row[UserTicketTable.user] = userId
                            row[UserTicketTable.ticket] = ticketId
                        }
                    }
                }

                val linked = ticket != null && user != null
                Pair(linked, linked)
            }
        } ?: false
    }

    /**
     * Adds each ticket's customer and tech, looked up in two queries rather than two per ticket
     */
    fun summarize(tickets: List<Ticket>): List<TicketSummary> {
        if (tickets.isEmpty()) return emptyList()

        return appLogTryCatch<List<TicketSummary>>(SUMMARIZE_TAG) {
            transaction {
                val identifiers = tickets.map { it.identifier }

                val customers = (CustomerTable innerJoin CustomerTicketTable)
                    .select(CustomerTicketTable.ticket, CustomerTable.id, CustomerTable.name)
                    .where { CustomerTicketTable.ticket inList identifiers }
                    .associate { row ->
                        row[CustomerTicketTable.ticket].value to EntityReference(row[CustomerTable.id].value, row[CustomerTable.name])
                    }

                val users = (UserTable innerJoin UserTicketTable)
                    .select(UserTicketTable.ticket, UserTable.id, UserTable.name)
                    .where { UserTicketTable.ticket inList identifiers }
                    .associate { row ->
                        row[UserTicketTable.ticket].value to EntityReference(row[UserTable.id].value, row[UserTable.name])
                    }

                val summaries = tickets.map { ticket ->
                    TicketSummary(ticket, customers[ticket.identifier], users[ticket.identifier])
                }

                Pair(summaries, true)
            }
        } ?: tickets.map { TicketSummary(it) }
    }

    fun getUser(ticketId: Uuid): EntityReference? {
        var result: EntityReference? = null

        appLogTryCatch(GET_USER_TAG) {
            transaction {
                result = UserEntity
                    .wrapRows(
                        UserTable
                            .join(
                                UserTicketTable,
                                JoinType.INNER,
                                UserTable.id,
                                UserTicketTable.user
                            )
                            .selectAll()
                            .where { UserTicketTable.ticket eq ticketId }
                            .limit(1)
                    )
                    .singleOrNull()
                    ?.toIdentity()
            }

            result != null
        }

        return result
    }

    fun getCustomer(ticketId: Uuid): EntityReference? {
        var result: EntityReference? = null

        appLogTryCatch(GET_CUSTOMER_TAG) {
            transaction {
                result = CustomerEntity
                    .wrapRows(
                        CustomerTable
                            .join(
                                CustomerTicketTable,
                                JoinType.INNER,
                                CustomerTable.id,
                                CustomerTicketTable.customer
                            )
                            .selectAll()
                            .where { CustomerTicketTable.ticket eq ticketId }
                            .limit(1)
                    )
                    .singleOrNull()
                    ?.toIdentity()
            }

            result != null
        }

        return result
    }

    companion object {
        // Shared by every instance so numbering stays one at a time
        private val NUMBER_LOCK = Any()
    }
}

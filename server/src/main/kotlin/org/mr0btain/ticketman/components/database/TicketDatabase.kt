package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.Column
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
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.customer.CustomerEntity
import org.mr0btain.ticketman.components.database.tables.invoice.InvoicePaymentTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceTable
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
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
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.StatusLink
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.error.CustomerError
import org.mr0btain.ticketman.error.TicketError
import org.mr0btain.ticketman.error.UserError
import org.mr0btain.ticketman.logging.AppLogger
import java.security.SecureRandom
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.components.database.tables.ticket.TicketNoteTable

/**
 * @param currentYear Year new ticket numbers are made in, swappable so tests can cross a year
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class TicketDatabase(
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
    private val GET_PAGED_FOR_USER_TAG = "$tag:GetPagedForUser"
    private val MARK_PICKED_UP_TAG = "$tag:MarkPickedUp"
    private val STATUS_TOKEN_TAG = "$tag:StatusToken"
    private val BACKFILL_TOKENS_TAG = "$tag:BackfillStatusTokens"
    private val READY_TAG = "$tag:ReadyForPickup"
    private val SET_ESTIMATE_TAG = "$tag:SetEstimate"
    private val MOVE_TAG = "$tag:Move"

    override fun getById(identifier: Uuid): Ticket? =
        dbQuery(GET_BY_ID_TAG) {
            TicketEntity.findById(identifier)?.toTicket()
                ?: null.also { AppLogger.i(GET_BY_ID_TAG, TicketError.NOT_FOUND) }
        }

    /** Newest first, only the page's location's when it names one */
    override fun getPagedItems(request: PagedRequest): List<Ticket>? =
        dbQuery(GET_PAGED_ITEMS_TAG) {
            TicketEntity.find { TicketTable.location.at(request.location) }
                .orderBy(TicketTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toTicket() }
        }

    /**
     * A page of the tickets assigned to a user, newest first like [getPagedItems]
     * @param userId Identifier of the technician
     */
    fun getPagedItemsForUser(userId: Uuid, request: PagedRequest): List<Ticket>? =
        dbQuery(GET_PAGED_FOR_USER_TAG) {
            TicketEntity
                .wrapRows(
                    TicketTable
                        .join(UserTicketTable, JoinType.INNER, TicketTable.id, UserTicketTable.ticket)
                        .selectAll()
                        .where { (UserTicketTable.user eq userId) and TicketTable.location.at(request.location) }
                        .orderBy(TicketTable.id to SortOrder.DESC)
                        .offset(request.offset)
                        .limit(request.itemsPerPage)
                )
                .map { it.toTicket() }
        }

    override fun count(): Long =
        dbQuery(COUNT_TAG) { TicketEntity.count() } ?: 0L

    override fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) {
            val ticketEntity = TicketEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(REMOVE_TAG, TicketError.NOT_FOUND) }

            // The technician link has no cascade, so it goes first or the delete fails
            val notes = ticketEntity.notes.toList()
            val label = ticketEntity.toTicket().label
            UserTicketTable.deleteWhere { UserTicketTable.ticket eq identifier }
            keepOrDeleteInvoice(identifier, label)
            ticketEntity.delete()
            deleteNotes(notes)
            true
        }

    /**
     * An invoice with money against it, or a void one, outlives its ticket, keeping the ticket's number so it can
     * still be told apart. An open one with nothing paid goes with the ticket. Inside the ticket's delete
     */
    private fun keepOrDeleteInvoice(ticketIdentifier: Uuid, ticketLabel: String) {
        val invoice = InvoiceTable.selectAll().where { InvoiceTable.ticket eq ticketIdentifier }.firstOrNull() ?: return
        val invoiceIdentifier = invoice[InvoiceTable.id]
        val paid = !InvoicePaymentTable.selectAll().where { InvoicePaymentTable.invoice eq invoiceIdentifier }.empty()

        if (paid || invoice[InvoiceTable.status] != InvoiceStatus.OPEN.name) {
            InvoiceTable.update(where = { InvoiceTable.id eq invoiceIdentifier }) { row ->
                row[InvoiceTable.ticketLabel] = ticketLabel
                row[InvoiceTable.ticketRemovedAt] = Clock.System.now().toEpochMilliseconds()
            }
        } else {
            // Its lines and payments go with it
            InvoiceTable.deleteWhere { InvoiceTable.id eq invoiceIdentifier }
        }
    }

    override fun search(query: String, limit: Int): List<TmSearchResult>? =
        searchTickets(query, limit)?.map { ticket ->
            TmSearchResult(
                identifier = ticket.identifier,
                text = "${ticket.label} ${ticket.device}".trim()
            )
        }

    /**
     * Tickets matching a number ("26-42" exact, "42" any year), part of the id, device make/model/serial or problem.
     * A status page link (the claim slip's QR, scanned) finds just its ticket. Newest number first
     */
    fun searchTickets(query: String, limit: Int): List<Ticket>? =
        dbQuery(SEARCH_TAG) {
            StatusLink.tokenIn(query)?.let { token ->
                return@dbQuery TicketEntity.find { TicketTable.statusToken eq token }.limit(1).map { it.toTicket() }
            }

            val text = query.trim().lowercase()
            val contains = "%$text%"

            // "26-42" is an exact number, at the main location, "dt-26-42" at that one. Plain digits match a sequence
            // in any year anywhere
            val numberMatch: Op<Boolean> = TicketNumber.parse(text)
                ?.let { (TicketTable.numberPrefix eq it.prefix) and (TicketTable.numberYear eq it.year) and (TicketTable.numberSequence eq it.sequence) }
                ?: text.toLongOrNull()?.let { TicketTable.numberSequence eq it }
                ?: Op.FALSE

            TicketEntity
                .find {
                    numberMatch or
                        (TicketTable.id.castTo(VarCharColumnType()) like "$text%") or
                        (TicketTable.device.castTo(VarCharColumnType()).lowerCase() like contains) or
                        (TicketTable.problem.lowerCase() like contains)
                }
                .orderBy(TicketTable.numberYear to SortOrder.DESC, TicketTable.numberSequence to SortOrder.DESC)
                .limit(limit)
                .map { ticketEntity -> ticketEntity.toTicket() }
        }

    // One at a time so two counters can't be handed the same number
    override fun add(data: Ticket): Boolean =
        synchronized(NUMBER_LOCK) {
            dbWrite(ADD_TAG) {
                insertTicket(data)
                true
            }
        }

    /**
     * Runs [block] holding the numbering lock, for an outer transaction that adds a ticket. It has to be held until
     * that transaction commits: until then nobody else can see the new number, so they'd be handed it again
     */
    fun <T> whileNumbering(block: () -> T): T = synchronized(NUMBER_LOCK, block)

    // Must be called inside a transaction while holding NUMBER_LOCK. Numbered at its location
    private fun insertTicket(data: Ticket) {
        val year = currentYear()
        val prefix = numberPrefix(data.locationIdentifier)

        TicketEntity.new(data.identifier) {
            device = data.device
            status = data.status
            priority = data.priority
            numberYear = year
            numberPrefix = prefix
            numberSequence = nextSequence(year, prefix)
            location = data.locationIdentifier
            problem = data.problem
            condition = data.condition
            accessories = data.accessories
            estimateCents = data.estimateCents
            promisedAt = data.promisedAt
            deviceRef = data.deviceIdentifier
            group = data.groupIdentifier
            checklist = data.checklist
            warrantyOf = data.warrantyOf
            // Never the client's, so nobody picks a link they know
            statusToken = newStatusToken()
        }
    }

    private fun nextSequence(year: Int, prefix: String = ""): Long {
        val max = TicketTable.numberSequence.max()

        return (TicketTable
            .select(max)
            .where { (TicketTable.numberYear eq year) and (TicketTable.numberPrefix eq prefix) }
            .singleOrNull()
            ?.get(max) ?: 0L) + 1
    }

    /**
     * Numbers tickets made before numbering existed, oldest first, in the year they were made
     */
    fun backfillNumbers() {
        synchronized(NUMBER_LOCK) {
            dbWrite(BACKFILL_TAG) {
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

                true
            }
        }
    }

    /**
     * Gives tickets made before status links one each
     */
    fun backfillStatusTokens() {
        dbWrite(BACKFILL_TOKENS_TAG) {
            TicketEntity.find { TicketTable.statusToken.isNull() }.forEach { ticketEntity ->
                ticketEntity.statusToken = newStatusToken()
            }

            true
        }
    }

    /** The ticket whose status page link has [token], null for a token that isn't one or was replaced */
    fun getByStatusToken(token: String): Ticket? {
        if (!StatusLink.isToken(token)) return null

        return dbQuery(STATUS_TOKEN_TAG) {
            TicketEntity.find { TicketTable.statusToken eq token }.firstOrNull()?.toTicket()
        }
    }

    /**
     * Gives the ticket a new status token, so a link handed out before stops working
     * @return The new token, null when there's no such ticket
     */
    fun replaceStatusToken(identifier: Uuid): String? {
        val token = newStatusToken()

        return token.takeIf {
            dbWrite(STATUS_TOKEN_TAG) {
                val ticketEntity = TicketEntity.findById(identifier)
                    ?: return@dbWrite false.also { AppLogger.i(STATUS_TOKEN_TAG, TicketError.NOT_FOUND) }

                ticketEntity.statusToken = token
                true
            }
        }
    }

    /** Saves the check in signature, replacing any the ticket had */
    fun setSignature(identifier: Uuid, signature: TicketSignature): Boolean =
        dbWrite(SIGNATURE_TAG) {
            TicketSignatureTable.deleteWhere { TicketSignatureTable.ticket eq identifier }
            TicketSignatureTable.insert { row ->
                row[TicketSignatureTable.ticket] = identifier
                row[TicketSignatureTable.signature] = signature
            }

            true
        }

    fun getSignature(identifier: Uuid): TicketSignature? =
        dbQuery(SIGNATURE_TAG) {
            TicketSignatureTable
                .selectAll()
                .where { TicketSignatureTable.ticket eq identifier }
                .firstOrNull()
                ?.get(TicketSignatureTable.signature)
        }

    /** Closes a ticket as picked up at [time] */
    /** @param warrantyUntil When its repair's warranty runs out, null for none */
    fun markPickedUp(identifier: Uuid, time: Long, warrantyUntil: Long? = null): Boolean =
        dbWrite(MARK_PICKED_UP_TAG) {
            val ticket = TicketEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(MARK_PICKED_UP_TAG, TicketError.NOT_FOUND) }

            ticket.status = TicketStatus(TicketStatusType.PICKED_UP, time)
            ticket.pickedUpAt = time
            ticket.warrantyUntil = warrantyUntil
            true
        }

    /**
     * Sends a ticket to another location. Its number stays, it's still the same repair. Its invoice goes too while
     * it's open, so its parts come out of that location's stock at pickup
     * @return Whether there's such a ticket
     */
    fun moveTo(identifier: Uuid, location: Uuid): Boolean =
        dbWrite(MOVE_TAG) {
            val ticket = TicketEntity.findById(identifier) ?: return@dbWrite false
            ticket.location = location
            InvoiceTable.update(where = { (InvoiceTable.ticket eq identifier) and (InvoiceTable.status eq InvoiceStatus.OPEN.name) }) { row ->
                row[InvoiceTable.location] = location
            }
            true
        }

    /** Tickets waiting to be picked up, ready longest first */
    fun readyForPickup(): List<Ticket>? =
        dbQuery(READY_TAG) {
            TicketEntity.find { TicketTable.pickedUpAt.isNull() and TicketTable.abandonedAt.isNull() }
                .map { it.toTicket() }
                .filter { it.status.status == TicketStatusType.DONE }
                .sortedBy { it.status.time }
        }

    /** Treats the ticket as abandoned from [time], left too long after it was ready */
    fun markAbandoned(identifier: Uuid, time: Long): Boolean =
        dbWrite(READY_TAG) {
            val ticket = TicketEntity.findById(identifier) ?: return@dbWrite false
            ticket.abandonedAt = time
            true
        }

    /** Sets the quoted price, like when the customer approves an estimate */
    fun setEstimate(identifier: Uuid, cents: Long?): Boolean =
        dbWrite(SET_ESTIMATE_TAG) {
            val ticket = TicketEntity.findById(identifier)
                ?: return@dbWrite false.also { AppLogger.i(SET_ESTIMATE_TAG, TicketError.NOT_FOUND) }

            ticket.estimateCents = cents
            true
        }

    fun getByNumber(number: TicketNumber): Ticket? =
        dbQuery(GET_BY_NUMBER_TAG) {
            TicketEntity
                .find { (TicketTable.numberPrefix eq number.prefix) and (TicketTable.numberYear eq number.year) and (TicketTable.numberSequence eq number.sequence) }
                .firstOrNull()
                ?.toTicket()
        }

    /**
     * Adds an entry to a ticket's status history
     * @param changedBy Username of whoever made the change
     */
    fun recordStatusChange(identifier: Uuid, status: TicketStatus, changedBy: String): Boolean =
        dbWrite(RECORD_STATUS_TAG) {
            TicketStatusHistoryTable.insert { row ->
                row[TicketStatusHistoryTable.id] = Uuid.generateV7()
                row[TicketStatusHistoryTable.ticket] = identifier
                row[TicketStatusHistoryTable.status] = status.status.name
                row[TicketStatusHistoryTable.custom] = status.custom
                row[TicketStatusHistoryTable.time] = Clock.System.now().toEpochMilliseconds()
                row[TicketStatusHistoryTable.changedBy] = changedBy
            }

            true
        }

    /** Status changes oldest first */
    fun getStatusHistory(identifier: Uuid): List<TicketStatusChange>? =
        dbQuery(STATUS_HISTORY_TAG) {
            TicketStatusHistoryTable
                .selectAll()
                .where { TicketStatusHistoryTable.ticket eq identifier }
                .orderBy(TicketStatusHistoryTable.time to SortOrder.ASC, TicketStatusHistoryTable.id to SortOrder.ASC)
                .map { row ->
                    TicketStatusChange(
                        status = TicketStatusType.valueOf(row[TicketStatusHistoryTable.status]),
                        time = row[TicketStatusHistoryTable.time],
                        changedBy = row[TicketStatusHistoryTable.changedBy],
                        custom = row[TicketStatusHistoryTable.custom]
                    )
                }
        }

    override fun update(data: Ticket): Boolean =
        dbWrite(UPDATE_TAG) {
            val item = TicketEntity.findById(data.identifier)
                ?: return@dbWrite false.also { AppLogger.i(UPDATE_TAG, TicketError.NOT_FOUND) }

            // Number, status token and pick up time are owned by the server, so they aren't taken from the client. Nor a
            // picked up status: only pickup sets it, and once set it stays, even if the client sent an older status
            item.device = data.device
            if (item.status.status != TicketStatusType.PICKED_UP && data.status.status != TicketStatusType.PICKED_UP) {
                // Moved on by hand, so it's not abandoned any more and its ready time starts again
                if (item.status.choice != data.status.choice) item.abandonedAt = null
                item.status = data.status
            }
            item.priority = data.priority
            item.problem = data.problem
            item.condition = data.condition
            item.accessories = data.accessories
            item.estimateCents = data.estimateCents
            item.promisedAt = data.promisedAt
            item.checklist = data.checklist

            true
        }

    override val noteLinkColumn: Column<EntityID<Uuid>> = TicketNoteTable.note

    override fun addNote(identifier: Uuid, note: Note): Boolean =
        attachNote(note, TicketError.NOT_FOUND, { TicketEntity.findById(identifier) }) { ticketEntity, noteEntity ->
            ticketEntity.notes = SizedCollection(ticketEntity.notes + noteEntity)
        }

    override fun getNotes(identifier: Uuid): List<Note>? =
        dbQuery(GET_NOTES_TAG) {
            TicketEntity.findById(identifier)?.notes?.map { noteEntity -> noteEntity.toNote() }
                ?: null.also { AppLogger.i(GET_NOTES_TAG, TicketError.NOT_FOUND) }
        }

    /**
     * Links a ticket to a customer, replacing any customer it had
     * @return Whether both exist
     */
    fun setCustomer(ticketId: Uuid, customerId: Uuid): Boolean =
        dbWrite(SET_CUSTOMER_TAG) {
            when {
                TicketEntity.findById(ticketId) == null -> false.also { AppLogger.i(SET_CUSTOMER_TAG, TicketError.NOT_FOUND) }
                CustomerEntity.findById(customerId) == null -> false.also { AppLogger.i(SET_CUSTOMER_TAG, CustomerError.NOT_FOUND) }
                else -> {
                    CustomerTicketTable.deleteWhere { CustomerTicketTable.ticket eq ticketId }
                    CustomerTicketTable.insert { row ->
                        row[CustomerTicketTable.customer] = customerId
                        row[CustomerTicketTable.ticket] = ticketId
                    }
                    true
                }
            }
        }

    /**
     * Assigns a ticket to a tech, replacing whoever had it
     * @return Whether both exist
     */
    fun setUser(ticketId: Uuid, userId: Uuid): Boolean =
        dbWrite(SET_USER_TAG) {
            when {
                TicketEntity.findById(ticketId) == null -> false.also { AppLogger.i(SET_USER_TAG, TicketError.NOT_FOUND) }
                UserEntity.findById(userId) == null -> false.also { AppLogger.i(SET_USER_TAG, UserError.NOT_FOUND) }
                else -> {
                    UserTicketTable.deleteWhere { UserTicketTable.ticket eq ticketId }
                    UserTicketTable.insert { row ->
                        row[UserTicketTable.user] = userId
                        row[UserTicketTable.ticket] = ticketId
                    }
                    true
                }
            }
        }

    /**
     * Adds each ticket's customer and tech, looked up in two queries rather than two per ticket
     */
    fun summarize(tickets: List<Ticket>): List<TicketSummary> {
        if (tickets.isEmpty()) return emptyList()

        return dbQuery(SUMMARIZE_TAG) {
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

            tickets.map { ticket ->
                TicketSummary(ticket, customers[ticket.identifier], users[ticket.identifier])
            }
        } ?: tickets.map { TicketSummary(it) }
    }

    fun getUser(ticketId: Uuid): EntityReference? =
        dbQuery(GET_USER_TAG) {
            UserEntity
                .wrapRows(
                    UserTable
                        .join(UserTicketTable, JoinType.INNER, UserTable.id, UserTicketTable.user)
                        .selectAll()
                        .where { UserTicketTable.ticket eq ticketId }
                        .limit(1)
                )
                .singleOrNull()
                ?.toIdentity()
        }

    fun getCustomer(ticketId: Uuid): EntityReference? =
        dbQuery(GET_CUSTOMER_TAG) {
            CustomerEntity
                .wrapRows(
                    CustomerTable
                        .join(CustomerTicketTable, JoinType.INNER, CustomerTable.id, CustomerTicketTable.customer)
                        .selectAll()
                        .where { CustomerTicketTable.ticket eq ticketId }
                        .limit(1)
                )
                .singleOrNull()
                ?.toIdentity()
        }

    companion object {
        // Shared by every instance so numbering stays one at a time
        private val NUMBER_LOCK = Any()

        private val random = SecureRandom()

        /** 16 random bytes as hex: lowercase, so it survives the search's lowercasing, and safe in a link */
        fun newStatusToken(): String {
            val bytes = ByteArray(StatusLink.TOKEN_LENGTH / 2).also { random.nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Lookups for the shop counter that cross tickets and customers
 * @param zone Time zone that decides where "today" starts, the server's by default
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class CounterDatabase(
    private val ticketDatabase: TicketDatabase,
    private val customerDatabase: CustomerDatabase,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val TAG = "CounterDatabase"
    private val QUEUE_TAG = "$TAG:Queue"
    private val REPEAT_TAG = "$TAG:RepeatCheck"
    private val CUSTOMER_TICKETS_TAG = "$TAG:CustomerTickets"

    /**
     * One search box for everything. Tickets that matched come first, then the tickets of customers that matched,
     * so typing a phone number brings up that customer's tickets too
     */
    fun search(query: String, limit: Int): CounterSearchResponse {
        if (query.isBlank()) return CounterSearchResponse()

        val customers = customerDatabase.searchCustomers(query, limit).orEmpty()
        val matchedTickets = ticketDatabase.searchTickets(query, limit).orEmpty()
        val customerTickets = ticketsForCustomers(customers.map { it.identifier })

        val tickets = (matchedTickets + customerTickets)
            .distinctBy { it.identifier }
            .take(limit)

        return CounterSearchResponse(
            tickets = ticketDatabase.summarize(tickets),
            customers = customers
        )
    }

    /**
     * Tickets that need attention at the counter. Picked up tickets are left out
     * @param recentLimit How many of the newest tickets to include
     * @param location Only this location's tickets, null for every location's
     */
    fun queue(recentLimit: Int, location: Uuid? = null): CounterQueueResponse {
        val today = Instant.now().atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val tickets = dbQuery(QUEUE_TAG) {
            TicketEntity
                .find { TicketTable.pickedUpAt.isNull() and TicketTable.location.at(location) }
                .orderBy(TicketTable.id to SortOrder.DESC)
                .map { it.toTicket() }
                .filter { it.status.status != TicketStatusType.PICKED_UP }
        }.orEmpty()

        val open = tickets.filter { it.status.status.isOpen }

        return CounterQueueResponse(
            readyForPickup = ticketDatabase.summarize(
                tickets.filter { it.status.status == TicketStatusType.DONE && it.abandonedAt == null }.sortedBy { it.status.time }
            ),
            abandoned = ticketDatabase.summarize(
                tickets.filter { it.status.status == TicketStatusType.DONE && it.abandonedAt != null }.sortedBy { it.abandonedAt }
            ),
            dueToday = ticketDatabase.summarize(
                open.filter { ticket -> ticket.promisedAt?.let { it in startOfToday until startOfTomorrow } == true }
                    .sortedBy { it.promisedAt }
            ),
            overdue = ticketDatabase.summarize(
                open.filter { ticket -> ticket.promisedAt?.let { it < startOfToday } == true }
                    .sortedBy { it.promisedAt }
            ),
            recentlyCheckedIn = ticketDatabase.summarize(tickets.take(recentLimit))
        )
    }

    /**
     * Earlier tickets for the same customer and device made within the window, newest first. Matches on serial
     * when one is given, otherwise make and model. A ticket's time is when it was picked up, or made if it wasn't
     */
    fun repeatCheck(customerIdentifier: Uuid, device: Device, windowDays: Int, excludeTicket: Uuid?, deviceIdentifier: Uuid? = null): List<TicketSummary> {
        val since = (Clock.System.now() - windowDays.days).toEpochMilliseconds()
        val serial = device.serial.trim()

        if (deviceIdentifier == null && serial.isBlank() && (device.manufacturer.isBlank() || device.model.isBlank())) return emptyList()

        val matches = dbQuery(REPEAT_TAG) {
            val identifiers = CustomerTicketTable
                .select(CustomerTicketTable.ticket)
                .where {
                    val forCustomer = CustomerTicketTable.customer eq customerIdentifier
                    if (excludeTicket != null) forCustomer and (CustomerTicketTable.ticket neq excludeTicket) else forCustomer
                }
                .map { it[CustomerTicketTable.ticket].value }

            val tickets = TicketEntity
                .find { TicketTable.id inList identifiers }
                .map { it.toTicket() }
                .filter { ticket ->
                    val previous = ticket.device

                    // The same device record is the surest match, then the serial, then make and model
                    if (deviceIdentifier != null && ticket.deviceIdentifier == deviceIdentifier) {
                        true
                    } else if (serial.isNotBlank()) {
                        previous.serial.trim().equals(serial, ignoreCase = true)
                    } else {
                        previous.manufacturer.trim().equals(device.manufacturer.trim(), ignoreCase = true) &&
                            previous.model.trim().equals(device.model.trim(), ignoreCase = true)
                    }
                }
                .filter { ticket -> (ticket.pickedUpAt ?: ticket.identifier.timestampMillis()) >= since }
                .sortedByDescending { ticket -> ticket.pickedUpAt ?: ticket.identifier.timestampMillis() }

            tickets
        }.orEmpty()

        return ticketDatabase.summarize(matches)
    }

    /** A customer's tickets, newest first */
    fun customerTickets(customerIdentifier: Uuid): List<TicketSummary> =
        ticketDatabase.summarize(ticketsForCustomers(listOf(customerIdentifier)))

    private fun ticketsForCustomers(customers: List<Uuid>): List<Ticket> {
        if (customers.isEmpty()) return emptyList()

        return dbQuery(CUSTOMER_TICKETS_TAG) {
            val identifiers = CustomerTicketTable
                .select(CustomerTicketTable.ticket)
                .where { CustomerTicketTable.customer inList customers }
                .map { it[CustomerTicketTable.ticket].value }

            TicketEntity
                .find { TicketTable.id inList identifiers }
                .orderBy(TicketTable.id to SortOrder.DESC)
                .map { it.toTicket() }
        }.orEmpty()
    }
}

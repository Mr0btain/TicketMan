package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoicePaymentTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.DailyReport
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Read only queries across tables for the admin screen
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class StatsDatabase(private val inventory: InventoryDatabase = InventoryDatabase()) {
    private val TAG = "StatsDatabase"
    private val GET_STATS_TAG = "$TAG:GetStats"
    private val GET_LOW_STOCK_TAG = "$TAG:GetLowStock"
    private val DAILY_REPORT_TAG = "$TAG:DailyReport"

    /**
     * Builds the admin overview numbers
     * @param lowStockThreshold Inventory at or below this stock counts as low
     * @param recentLimit How many of the newest tickets to include
     * @param activeSessions Passed in since sessions live in the token database
     * @param location Tickets and stock at this location only, every location's for null. Customers and staff are
     * the company's either way
     */
    fun getAdminStats(lowStockThreshold: Int, recentLimit: Int, activeSessions: Long, location: Uuid? = null): AdminStatsResponse? {
        val lowStockCount = inventory.lowStock(lowStockThreshold, location)?.size?.toLong() ?: 0
        return dbQuery(GET_STATS_TAG) {
            // Status and priority are json columns so group them here rather than in sql
            val tickets = TicketTable
                .select(TicketTable.id, TicketTable.status, TicketTable.priority)
                .where { ticketsAt(location) }
                .map { row -> Triple(row[TicketTable.id].value, row[TicketTable.status].status, row[TicketTable.priority].priority) }

            val openTickets = tickets.filter { (_, status, _) -> status.isOpen }
            val weekAgo = (Clock.System.now() - 7.days).toEpochMilliseconds()

            // Identifiers are v7 so their embedded time is when the ticket was made
            val oldestOpenTicket = openTickets
                .minByOrNull { (identifier, _, _) -> identifier.timestampMillis() }
                ?.let { (identifier, _, _) -> TicketEntity.findById(identifier)?.toTicket() }

            val recentTickets = TicketEntity
                .find { ticketsAt(location) }
                .orderBy(TicketTable.id to SortOrder.DESC)
                .limit(recentLimit)
                .map { it.toTicket() }

            val users = UserTable
                .select(UserTable.access, UserTable.status)
                .map { row -> row[UserTable.access] to row[UserTable.status] }

            val activeLevels = users.filter { (_, status) -> status == UserStatus.ACTIVE.name }.map { (level, _) -> level }

            val stats = AdminStatsResponse(
                totalTickets = tickets.size.toLong(),
                openTickets = openTickets.size.toLong(),
                ticketsCreatedLastWeek = tickets.count { (identifier, _, _) -> identifier.timestampMillis() >= weekAgo }.toLong(),
                ticketsByStatus = TicketStatusType.entries.associateWith { type ->
                    tickets.count { (_, status, _) -> status == type }.toLong()
                },
                ticketsByPriority = TicketPriorityType.entries.associateWith { type ->
                    openTickets.count { (_, _, priority) -> priority == type }.toLong()
                },
                oldestOpenTicket = oldestOpenTicket,
                recentTickets = recentTickets,
                totalCustomers = CustomerTable.selectAll().count(),
                totalInventory = InventoryTable.selectAll().count(),
                lowStockCount = lowStockCount,
                lowStockThreshold = lowStockThreshold,
                totalUsers = users.size.toLong(),
                usersByAccessLevel = listOf(AccessLevel.RESTRICTED, AccessLevel.NORMAL, AccessLevel.ADMIN)
                    .associateWith { level -> activeLevels.count { it == level }.toLong() },
                pendingUsers = users.count { (_, status) -> status == UserStatus.PENDING.name }.toLong(),
                deactivatedUsers = users.count { (_, status) -> status == UserStatus.DEACTIVATED.name }.toLong(),
                activeSessions = activeSessions
            )

            stats
        }
    }

    /**
     * Money taken and work done on [date], in [zone]. The overdue and waiting counts are as of right now
     */
    fun dailyReport(date: LocalDate, zone: ZoneId = ZoneId.systemDefault(), location: Uuid? = null): DailyReport? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        return dbQuery(DAILY_REPORT_TAG) {
            val payments = (InvoicePaymentTable innerJoin InvoiceTable)
                .select(InvoicePaymentTable.method, InvoicePaymentTable.amountCents)
                .where { (InvoicePaymentTable.receivedAt greaterEq start) and (InvoicePaymentTable.receivedAt less end) and invoicesAt(location) }
                .map { PaymentMethod.valueOf(it[InvoicePaymentTable.method]) to it[InvoicePaymentTable.amountCents] }

            val paidInvoices = InvoiceTable
                .select(InvoiceTable.totalCents, InvoiceTable.taxCents)
                .where {
                    (InvoiceTable.status eq InvoiceStatus.PAID.name) and
                        (InvoiceTable.paidAt greaterEq start) and (InvoiceTable.paidAt less end) and invoicesAt(location)
                }
                .map { it[InvoiceTable.totalCents] to it[InvoiceTable.taxCents] }

            // Check in time is in the ticket's identifier, the rest are json, so these are sorted out here
            val tickets = TicketEntity.find { ticketsAt(location) }.map { it.toTicket() }

            val report = DailyReport(
                date = date.toString(),
                takenByMethod = payments.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.sum() },
                paymentCount = payments.count { it.second > 0 },
                refundedCents = -payments.filter { it.second < 0 }.sumOf { it.second },
                refundCount = payments.count { it.second < 0 },
                salesCents = paidInvoices.sumOf { it.first },
                taxCents = paidInvoices.sumOf { it.second },
                invoicesPaid = paidInvoices.size,
                ticketsCheckedIn = tickets.count { it.identifier.timestampMillis() in start until end },
                ticketsPickedUp = tickets.count { ticket -> ticket.pickedUpAt?.let { it in start until end } == true },
                overdueNow = tickets.count { ticket -> ticket.status.status.isOpen && ticket.promisedAt?.let { it < startOfToday } == true },
                waitingForPickupNow = tickets.count { it.status.status == TicketStatusType.DONE }
            )

            report
        }
    }

    private fun ticketsAt(location: Uuid?): Op<Boolean> = TicketTable.location.at(location)

    private fun invoicesAt(location: Uuid?): Op<Boolean> = InvoiceTable.location.at(location)

    /**
     * Inventory items at or below their reorder point at [location], or the threshold when they have none there,
     * lowest stock first. For null, ones low at any location
     */
    fun getLowStock(threshold: Int, limit: Int, location: Uuid? = null): List<Inventory>? =
        inventory.lowStock(threshold, location)?.take(limit)
}

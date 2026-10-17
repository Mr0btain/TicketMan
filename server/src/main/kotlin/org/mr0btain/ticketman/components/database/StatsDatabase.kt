package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryEntity
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.error.appLogTryCatch
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi

/**
 * Read only queries across tables for the admin screen
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class StatsDatabase {
    private val TAG = "StatsDatabase"
    private val GET_STATS_TAG = "$TAG:GetStats"
    private val GET_LOW_STOCK_TAG = "$TAG:GetLowStock"

    /**
     * Builds the admin overview numbers
     * @param lowStockThreshold Inventory at or below this stock counts as low
     * @param recentLimit How many of the newest tickets to include
     * @param activeSessions Passed in since sessions live in the token database
     */
    fun getAdminStats(lowStockThreshold: Int, recentLimit: Int, activeSessions: Long): AdminStatsResponse? {
        return appLogTryCatch<AdminStatsResponse?>(GET_STATS_TAG) {
            transaction {
                // Status and priority are json columns so group them here rather than in sql
                val tickets = TicketTable
                    .select(TicketTable.id, TicketTable.status, TicketTable.priority)
                    .map { row -> Triple(row[TicketTable.id].value, row[TicketTable.status].status, row[TicketTable.priority].priority) }

                val openTickets = tickets.filter { (_, status, _) -> status != TicketStatusType.DONE }
                val weekAgo = (Clock.System.now() - 7.days).toEpochMilliseconds()

                // Identifiers are v7 so their embedded time is when the ticket was made
                val oldestOpenTicket = openTickets
                    .minByOrNull { (identifier, _, _) -> identifier.timestampMillis() }
                    ?.let { (identifier, _, _) -> TicketEntity.findById(identifier)?.toTicket() }

                val recentTickets = TicketEntity
                    .all()
                    .orderBy(TicketTable.id to SortOrder.DESC)
                    .limit(recentLimit)
                    .map { it.toTicket() }

                val users = UserTable
                    .select(UserTable.access)
                    .map { row -> row[UserTable.access] }

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
                    lowStockCount = InventoryTable.selectAll().where { InventoryTable.stock lessEq lowStockThreshold }.count(),
                    lowStockThreshold = lowStockThreshold,
                    totalUsers = users.size.toLong(),
                    usersByAccessLevel = AccessLevel.entries.associateWith { level -> users.count { it == level }.toLong() },
                    activeSessions = activeSessions
                )

                Pair(stats, true)
            }
        }
    }

    /**
     * Inventory items at or below the threshold, lowest stock first
     */
    fun getLowStock(threshold: Int, limit: Int): List<Inventory>? {
        return appLogTryCatch<List<Inventory>?>(GET_LOW_STOCK_TAG) {
            transaction {
                val items = InventoryEntity
                    .find { InventoryTable.stock lessEq threshold }
                    .orderBy(InventoryTable.stock to SortOrder.ASC, InventoryTable.name to SortOrder.ASC)
                    .limit(limit)
                    .map { it.toInventory() }

                Pair(items, true)
            }
        }
    }
}

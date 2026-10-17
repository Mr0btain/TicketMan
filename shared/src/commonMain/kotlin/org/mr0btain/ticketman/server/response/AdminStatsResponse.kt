package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType

/**
 * Numbers shown on the admin overview
 */
@Serializable
data class AdminStatsResponse(
    val totalTickets: Long = 0,
    val openTickets: Long = 0,
    val ticketsCreatedLastWeek: Long = 0,
    val ticketsByStatus: Map<TicketStatusType, Long> = emptyMap(),
    val ticketsByPriority: Map<TicketPriorityType, Long> = emptyMap(),
    val oldestOpenTicket: Ticket? = null,
    val recentTickets: List<Ticket> = emptyList(),
    val totalCustomers: Long = 0,
    val totalInventory: Long = 0,
    val lowStockCount: Long = 0,
    val lowStockThreshold: Int = 0,
    val totalUsers: Long = 0,
    /** Active users only */
    val usersByAccessLevel: Map<AccessLevel, Long> = emptyMap(),
    val pendingUsers: Long = 0,
    val deactivatedUsers: Long = 0,
    val activeSessions: Long = 0
)

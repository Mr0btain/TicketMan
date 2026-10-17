package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer

/**
 * Results for the counter's search bar. Tickets include ones belonging to the customers that matched
 */
@Serializable
data class CounterSearchResponse(
    val tickets: List<TicketSummary> = emptyList(),
    val customers: List<Customer> = emptyList()
)

/**
 * What the counter needs to keep an eye on
 * @param readyForPickup Done and waiting, longest waiting first
 * @param dueToday Still open and promised for today
 * @param overdue Still open and promised before today
 * @param recentlyCheckedIn Newest tickets first
 */
@Serializable
data class CounterQueueResponse(
    val readyForPickup: List<TicketSummary> = emptyList(),
    val dueToday: List<TicketSummary> = emptyList(),
    val overdue: List<TicketSummary> = emptyList(),
    val recentlyCheckedIn: List<TicketSummary> = emptyList()
)

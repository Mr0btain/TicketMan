package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.ticket.Ticket

/**
 * Results for the counter's search bar. Tickets include ones belonging to the customers that matched
 */
@Serializable
data class CounterSearchResponse(
    val tickets: List<TicketSummary> = emptyList(),
    val customers: List<Customer> = emptyList()
)

/** A checked in ticket with its number, and its invoice if a deposit was taken */
@Serializable
data class CheckInResponse(
    val ticket: Ticket,
    val invoice: Invoice? = null,
    /** The other tickets checked in with it, in the order sent */
    val linked: List<Ticket> = emptyList()
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
    val recentlyCheckedIn: List<TicketSummary> = emptyList(),
    /** Ready but left so long they're treated as abandoned, longest ago first */
    val abandoned: List<TicketSummary> = emptyList()
)

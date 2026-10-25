package org.mr0btain.ticketman.ui.screens.counter.components

import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.orNullIfNotFound
import org.mr0btain.ticketman.data.customer.CreditEntry
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.estimate.EstimateStatus
import org.mr0btain.ticketman.data.notify.OutboxMessage
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import kotlin.uuid.Uuid

/** Everything the ticket card shows */
data class TicketDetail(
    val ticket: Ticket,
    val customer: Customer?,
    val technician: EntityReference?,
    val notes: List<Note>,
    val history: List<TicketStatusChange>,
    val invoice: Invoice?,
    val signature: TicketSignature?,
    /** What the customer was sent, newest first. Null when it couldn't be read */
    val messages: List<OutboxMessage>? = emptyList(),
    /** Newest first. Null when they couldn't be read */
    val estimates: List<Estimate>? = emptyList(),
    /** Purchase orders with parts for it, newest first. Null when they couldn't be read */
    val purchases: List<PurchaseOrder>? = emptyList()
) {
    /** The estimate waiting for the customer */
    val pendingEstimate: Estimate?
        get() = estimates?.firstOrNull { it.status == EstimateStatus.PENDING }
}

/**
 * A customer for the counter's customer card
 * @param credit Their store credit history, newest first
 * @param invoices Their tickets' invoices and sales, newest first
 */
data class CustomerDetail(
    val customer: Customer,
    val tickets: List<Ticket>,
    val credit: List<CreditEntry> = emptyList(),
    val invoices: List<Invoice> = emptyList()
)

/**
 * A ticket and what goes with it, for the ticket card, pickup and printing. Fails if the ticket, its notes, or a
 * customer or tech it has can't be read
 */
suspend fun RemoteHandler.loadTicketDetail(identifier: Uuid): TmResult<TicketDetail, ServerError> {
    val repo = ticketRepo
    val ticket = repo.get(identifier).valueOr { return TmResult.Error(it) }

    val customer = repo.getCustomer(identifier).orNullIfNotFound().valueOr { return TmResult.Error(it) }
        ?.let { customerRepo.get(it.identifier).valueOr { error -> return TmResult.Error(error) } }

    return TmResult.Success(
        TicketDetail(
            ticket = ticket,
            customer = customer,
            technician = repo.getUser(identifier).orNullIfNotFound().valueOr { return TmResult.Error(it) },
            notes = repo.getNotes(identifier).valueOr { return TmResult.Error(it) },
            history = repo.getStatusHistory(identifier),
            invoice = invoiceRepo.getForTicket(identifier),
            signature = repo.getSignature(identifier),
            // Only shown, so the card still opens without them
            messages = notifyRepo.forTicket(identifier).getOrNull(),
            estimates = estimateRepo.forTicket(identifier).getOrNull(),
            purchases = purchaseRepo.forTicket(identifier).getOrNull()
        )
    )
}

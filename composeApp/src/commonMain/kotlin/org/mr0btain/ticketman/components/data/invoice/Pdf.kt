package org.mr0btain.ticketman.components.data.invoice

import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.ticket.Ticket

expect fun makeTicketInvoicePdf(
    filePath: String,
    fileName: String,
    customer: Customer,
    ticket: Ticket
)
package org.mr0btain.ticketman.components.data.invoice

import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Ticket

expect fun makeTicketInvoicePdf(
    filePath: String,
    fileName: String,
    customer: Customer,
    ticket: Ticket
)
package org.mr0btain.ticketman.components.data.components.invoice

import org.mr0btain.ticketman.data.customer.CustomerInfo
import org.mr0btain.ticketman.data.ticket.TicketInfo

expect fun makeTicketInvoicePdf(
    filePath: String,
    fileName: String,
    customerInfo: CustomerInfo,
    ticketInfo: TicketInfo
)
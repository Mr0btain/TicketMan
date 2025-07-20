package org.mr0btain.ticketman.components.data.invoice

import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.TicketInfo

expect fun makeTicketInvoicePdf(
    filePath: String,
    fileName: String,
    customerData: CustomerData,
    ticketInfo: TicketInfo
)
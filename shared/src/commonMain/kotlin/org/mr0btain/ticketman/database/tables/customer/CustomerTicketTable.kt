package org.mr0btain.ticketman.database.tables.customer

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.ticket.TicketTable

object CustomerTicketTable: Table("customer_tickets_table") {
    val customer = reference("customer_id", CustomerTable)
    val ticket = reference("ticket_id", TicketTable)

    override val primaryKey = PrimaryKey(customer, ticket)
}
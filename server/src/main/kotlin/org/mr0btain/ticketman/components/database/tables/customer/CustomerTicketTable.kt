package org.mr0btain.ticketman.components.database.tables.customer


import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable

object CustomerTicketTable: Table("customer_tickets_table") {
    val customer = reference("customer_id", CustomerTable, onDelete = ReferenceOption.CASCADE)
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(customer, ticket)

    init {
        index(isUnique = true, ticket)
    }
}
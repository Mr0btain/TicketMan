package org.mr0btain.ticketman.components.database.tables.user

import org.jetbrains.exposed.v1.core.Table
import org.mr0btain.ticketman.database.tables.ticket.TicketTable

object UserTicketTable: Table("user_ticket_table") {
    val user = reference("user_id", UserTable)
    val ticket = reference("ticket_id", TicketTable)

    override val primaryKey = PrimaryKey(user, ticket)
}
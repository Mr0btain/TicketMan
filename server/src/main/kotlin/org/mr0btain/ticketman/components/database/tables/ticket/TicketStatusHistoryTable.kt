package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/**
 * Every status a ticket has been set to, and who set it
 */
object TicketStatusHistoryTable : UuidTable("ticket_status_history") {
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE)
    val status = text("status")
    val time = long("time")
    val changedBy = text("changed_by")
}
